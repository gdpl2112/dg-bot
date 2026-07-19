package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import io.github.kloping.judge.Judge;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.utils.ExternalResource;
import okhttp3.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import io.github.kloping.date.FrameUtils;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author github.kloping
 */
@Slf4j
@Component
public class ShortVideoParse implements BaseOptional {
    public static final String REGX = "(https?|http|ftp|file)://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]";
    public static final Pattern PATTERN = Pattern.compile(REGX);
    public static final String KS_LINK = "v.kuaishou.com";
    public static final String DY_LINK = "v.douyin.com";
    public static final int BATCH_SIZE = 16;
    public static final RestTemplate TEMPLATE = new RestTemplate();
    public static final String API_URL = "http://119.45.132.231:8051/api/parse";
    public static final String DOUYIN_REFERE = "https://www.douyin.com/";

    /**
     * 图集数量超过该阈值时, 改为选择式发送
     */
    public static final int SELECT_THRESHOLD = 40;
    /**
     * 选择超时时间(毫秒), 超时后自动取消
     */
    public static final long SELECT_TIMEOUT_MS = 10 * 60 * 1000L;
    /**
     * 选择指令格式: 仅由数字/逗号/中文逗号/连字符/空白组成, 如 1,2,3 / 5 / 1-10 / 1-5,8,10
     */
    public static final Pattern SELECT_INPUT = Pattern.compile("^\\d[\\d,\uff0c\\-\\s]*$");
    /**
     * 待选择会话, key 为 subjectId_senderId
     */
    public static final Map<String, PendingImageSelection> PENDING = new ConcurrentHashMap<>();

    static {
        // 定时清理过期的选择会话, 并提醒超时
        FrameUtils.SERVICE.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, PendingImageSelection>> it = PENDING.entrySet().iterator();
            while (it.hasNext()) {
                PendingImageSelection p = it.next().getValue();
                if (now - p.time > SELECT_TIMEOUT_MS) {
                    it.remove();
                    try {
                        p.subject.sendMessage("实况图集选择已超时(" + (SELECT_TIMEOUT_MS / 60000) + "分钟), 已自动取消.");
                    } catch (Exception ex) {
                        log.warn("选择超时提醒发送失败: {}", ex.getMessage());
                    }
                }
            }
        }, 1, 1, TimeUnit.MINUTES);
    }


    // 创建一个单例的 OkHttpClient 实例
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .build();

    @Override
    public String getDesc() {
        return "自动检测消息中解析[快手/抖音][短视频/图集/实况图集]短链接并解析发送结果";
    }

    @Override
    public String getName() {
        return "短视频自动解析";
    }

    @Override
    public void run(MessageEvent event) {
        String out = io.github.gdpl2112.dg_bot.Utils.getLineString(event);
        // 优先处理待选择会话(选择序号/取消)
        if (handlePendingSelection(event, out)) return;
        if (out.contains(KS_LINK) || out.contains(DY_LINK)) {
            Matcher matcher = PATTERN.matcher(out);
            if (matcher.find()) {
                parseNow(matcher.group(), event);
            }
        }
    }

    public void parseNow(String url, MessageEvent event) {
        log.info("开始解析: {}", url);

        JxDataNew jxData;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.ALL));
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            Map<String, String> requestBody = Collections.singletonMap("text", url);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            String responseData = TEMPLATE.postForObject(API_URL, entity, String.class);
            jxData = JSON.parseObject(responseData, JxDataNew.class);
        } catch (Exception e) {
            event.getSubject().sendMessage("解析异常! 网络请求失败: " + e.getMessage());
            log.error("解析异常", e);
            return;
        }

        if (jxData == null || !Boolean.TRUE.equals(jxData.getSucc())
                || jxData.getRetcode() == null || jxData.getRetcode() != 200) {
            event.getSubject().sendMessage("解析异常!\n若链接无误请反馈.");
            log.warn("解析返回异常: {}", jxData);
            return;
        }

        NewApiData data = jxData.getData();
        if (data == null) {
            event.getSubject().sendMessage("解析异常!\n数据为空.");
            return;
        }

        Bot bot = event.getBot();
        var builder = new MessageChainBuilder();

        // 下载封面 (使用 OkHttp)
        byte[] coverBytes = null;
        if (Judge.isNotEmpty(data.getCoverUrl())) {
            try {
                coverBytes = downloadBytesWithOkHttp(data.getCoverUrl(), DOUYIN_REFERE);
                Image image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(coverBytes), "jpg");
                builder.append(image);
            } catch (IOException e) {
                log.error("封面下载异常: {}", e.getMessage(), e);
            }
        }

        builder.append("解析成功! 平台: ").append(data.getPlatform()).append("\n");
        if (data.getTitle() != null) builder.append(data.getTitle());
        if (data.getAuthor() != null && data.getAuthor().getNickname() != null)
            builder.append(" \\ ").append(data.getAuthor().getNickname());

        boolean isVideo = Judge.isNotEmpty(data.getVideoUrl());
        boolean hasImages = data.getImageList() != null && !data.getImageList().isEmpty();

        if (!isVideo && hasImages) {
            // ========== 图集 / 实况图集处理 ==========
            List<Object> imageList = data.getImageList();
            int totalImages = imageList.size();

            if (totalImages > SELECT_THRESHOLD) {
                // 图集数量过多, 改为选择式发送
                builder.append("\n检测到图集数量较多: ").append(String.valueOf(totalImages))
                        .append(" 张, 已开启选择式发送.")
                        .append("\n请回复要发送的序号, 支持格式: 1,2,3 或 5 或 1-10 (可组合, 如 1-5,8,10)")
                        .append("\n").append(String.valueOf(SELECT_TIMEOUT_MS / 60000))
                        .append("分钟内有效, 回复 '取消解析' 可取消选择.");
                event.getSubject().sendMessage(builder.build());

                PENDING.put(sessionKey(event), new PendingImageSelection(
                        imageList, coverBytes, data.getAudioUrl(),
                        event.getSubject(), event.getSender().getId(),
                        totalImages, System.currentTimeMillis()));
                return;
            }

            List<Integer> indices = new ArrayList<>(totalImages);
            for (int i = 0; i < totalImages; i++) indices.add(i);

            int totalBatches = (totalImages + BATCH_SIZE - 1) / BATCH_SIZE;
            if (totalBatches > 1) {
                builder.append("\n图片数量:").append(String.valueOf(totalImages))
                        .append("/分").append(String.valueOf(totalBatches)).append("批发送,请稍等...");
            } else {
                builder.append("\n图片数量:").append(String.valueOf(totalImages)).append("/正在发送,请稍等...");
            }
            event.getSubject().sendMessage(builder.build());

            sendImagesByIndices(event.getSubject(), bot, imageList, indices,
                    coverBytes, data.getAudioUrl(), false);

        } else if (isVideo) {
            // ========== 视频处理 ==========
            String videoUrl = data.getVideoUrl();
            try {
                // 使用 OkHttp 下载视频
                byte[] bytes = downloadBytesWithOkHttp(videoUrl, DOUYIN_REFERE);
                builder.append("\n视频大小:").append(toView(bytes.length)).append("\n正在发送,请稍等...: ");
                event.getSubject().sendMessage(builder.build());

                var fbuilder = new ForwardMessageBuilder(event.getSubject());
                if (coverBytes != null) {
                    String title = (data.getTitle() != null ? data.getTitle() : "video");
                    ShortVideo shortVideo = event.getSubject().uploadShortVideo(
                            ExternalResource.create(coverBytes),
                            ExternalResource.create(bytes),
                            title + ".mp4"
                    );
                    fbuilder.add(bot, shortVideo);
                }
                fbuilder.add(bot, new PlainText("视频直链: " + videoUrl));
                event.getSubject().sendMessage(fbuilder.build());
            } catch (IOException e) {
                event.getSubject().sendMessage("视频下载失败: " + e.getMessage());
                log.error("视频下载异常", e);
            } catch (Exception e) {
                event.getSubject().sendMessage("视频上传失败: " + e.getMessage());
                log.error("视频上传异常", e);
            }
        } else {
            event.getSubject().sendMessage("解析失败: 未找到有效的视频或图片内容");
        }
    }

    /**
     * 处理待选择会话: 取消 / 选择序号. 若已消费该消息返回 true.
     */
    private boolean handlePendingSelection(MessageEvent event, String out) {
        String key = sessionKey(event);
        PendingImageSelection pending = PENDING.get(key);
        if (pending == null) return false;

        if (out.equals("取消解析") || out.equals("取消发送") || out.equals("取消")) {
            PENDING.remove(key);
            event.getSubject().sendMessage("已取消本次实况图集选择.");
            return true;
        }

        // 不是选择指令(可能是新链接或无关消息), 交由后续逻辑处理
        if (!SELECT_INPUT.matcher(out).matches()) return false;

        if (System.currentTimeMillis() - pending.time > SELECT_TIMEOUT_MS) {
            PENDING.remove(key);
            event.getSubject().sendMessage("选择已超时, 请重新发送链接.");
            return true;
        }

        List<Integer> indices = parseSelection(out, pending.total);
        if (indices == null) {
            event.getSubject().sendMessage("序号格式有误, 请回复如: 1,2,3 或 5 或 1-10 (可组合)\n回复 '取消解析' 可取消.");
            return true;
        }
        if (indices.isEmpty()) {
            event.getSubject().sendMessage("未选择有效序号(可选 1-" + pending.total + "), 请重新输入.\n回复 '取消解析' 可取消.");
            return true;
        }

        PENDING.remove(key);
        event.getSubject().sendMessage("已选择 " + indices.size() + " 项, 正在发送,请稍等...");
        try {
            sendImagesByIndices(pending.subject, event.getBot(), pending.imageList,
                    indices, pending.coverBytes, pending.audioUrl, true);
        } catch (Exception e) {
            log.error("选择式发送异常", e);
            event.getSubject().sendMessage("发送时异常: " + e.getMessage());
        }
        return true;
    }

    /**
     * 解析选择字符串为 0 基序号集合(升序去重).
     * 格式非法返回 null; 无有效序号返回空列表.
     */
    private List<Integer> parseSelection(String input, int total) {
        try {
            TreeSet<Integer> set = new TreeSet<>();
            String[] parts = input.split("[,，\\s]+");
            for (String part : parts) {
                if (part.isEmpty()) continue;
                if (part.matches("\\d+-\\d+")) {
                    String[] ab = part.split("-");
                    int a = Integer.parseInt(ab[0]);
                    int b = Integer.parseInt(ab[1]);
                    if (a > b) {
                        int t = a;
                        a = b;
                        b = t;
                    }
                    for (int n = a; n <= b; n++) {
                        if (n >= 1 && n <= total) set.add(n - 1);
                    }
                } else if (part.matches("\\d+")) {
                    int n = Integer.parseInt(part);
                    if (n >= 1 && n <= total) set.add(n - 1);
                } else {
                    return null;
                }
            }
            return new ArrayList<>(set);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String sessionKey(MessageEvent event) {
        return event.getSubject().getId() + "_" + event.getSender().getId();
    }

    /**
     * 按指定的 0 基序号(原始顺序)分批以转发消息发送图片/实况视频.
     *
     * @param labelIndices 为 true 时, 在每张图片前附带其原始序号 #n
     */
    private void sendImagesByIndices(Contact subject, Bot bot, List<Object> imageList,
                                     List<Integer> indices, byte[] coverBytes,
                                     String audioUrl, boolean labelIndices) {
        int total = indices.size();
        int totalBatches = (total + BATCH_SIZE - 1) / BATCH_SIZE;

        for (int batch = 0; batch < totalBatches; batch++) {
            int start = batch * BATCH_SIZE;
            int end = Math.min(start + BATCH_SIZE, total);

            var fbuilder = new ForwardMessageBuilder(subject);
            if (batch == 0 && Judge.isNotEmpty(audioUrl)) {
                fbuilder.add(bot, new PlainText("音频直链:" + audioUrl));
            }
            if (totalBatches > 1) {
                fbuilder.add(bot, new PlainText("第" + (batch + 1) + "/" + totalBatches + "批"));
            }

            for (int i = start; i < end; i++) {
                int idx = indices.get(i);
                Object imgObj = imageList.get(idx);
                try {
                    String imageUrl;
                    String livePhotoUrl = null;

                    if (imgObj instanceof String) {
                        // Type 2: 普通图集
                        imageUrl = (String) imgObj;
                    } else if (imgObj instanceof Map) {
                        // Type 3: 实况图集
                        Map<?, ?> map = (Map<?, ?>) imgObj;
                        imageUrl = (String) map.get("url");
                        livePhotoUrl = (String) map.get("live_photo_url");
                    } else {
                        continue;
                    }

                    if (Judge.isEmpty(imageUrl) && Judge.isEmpty(livePhotoUrl)) continue;

                    if (labelIndices) {
                        fbuilder.add(bot, new PlainText("#" + (idx + 1)));
                    }

                    // 发送静态图片 (使用 OkHttp)
                    if (Judge.isNotEmpty(imageUrl)) {
                        try {
                            byte[] bytes = downloadBytesWithOkHttp(imageUrl, DOUYIN_REFERE);
                            Image image = Contact.uploadImage(subject, new ByteArrayInputStream(bytes), "jpg");
                            fbuilder.add(bot, image);
                        } catch (IOException e) {
                            log.error("图片下载失败: {}", e.getMessage(), e);
                            fbuilder.add(bot, new PlainText("[图片加载失败]"));
                        }
                    }

                    // 如果是实况图集，尝试上传为短视频，失败则发送直链
                    if (Judge.isNotEmpty(livePhotoUrl)) {
                        boolean uploaded = false;
                        try {
                            // 使用 OkHttp 下载实况视频
                            byte[] videoBytes = downloadBytesWithOkHttp(livePhotoUrl, DOUYIN_REFERE);
                            // 优先使用当前实况照片对应的静态图作为封面，若无则用全局封面
                            byte[] thumbBytes = Judge.isNotEmpty(imageUrl)
                                    ? downloadBytesWithOkHttp(imageUrl, DOUYIN_REFERE) // 也给静态图加 Referer
                                    : coverBytes;

                            if (thumbBytes == null || thumbBytes.length == 0) {
                                throw new IllegalStateException("无可用封面用于上传实况视频");
                            }

                            ShortVideo shortVideo = subject.uploadShortVideo(
                                    ExternalResource.create(thumbBytes),
                                    ExternalResource.create(videoBytes),
                                    "live_photo.mp4"
                            );
                            fbuilder.add(bot, shortVideo);
                            uploaded = true;
                        } catch (IOException e) {
                            log.warn("实况视频或封面下载失败, 回退为直链发送: {}", e.getMessage());
                        } catch (Exception uploadEx) {
                            log.warn("实况视频上传失败, 回退为直链发送: {}", uploadEx.getMessage());
                        }

                        // 上传失败或未执行上传时，以纯文本直链兜底
                        if (!uploaded) {
                            fbuilder.add(bot, new PlainText("[实况照片视频] " + livePhotoUrl));
                        }
                    }
                } catch (Exception ex) {
                    fbuilder.add(bot, new PlainText("[图片加载失败]"));
                    log.error("图片加载失败", ex);
                }
            }
            subject.sendMessage(fbuilder.build());
        }
    }

    /**
     * 使用 OkHttp 下载 URL 内容为字节数组，并设置 Referer
     *
     * @param url       要下载的 URL
     * @param referer   Referer 头部值
     * @return          下载的内容字节数组
     * @throws IOException 如果下载失败
     */
    public static byte[] downloadBytesWithOkHttp(String url, String referer) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Referer", referer)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + response.message());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("响应体为空");
            }
            return body.bytes(); // 注意：对于大文件，这会将整个内容加载到内存
        }
    }


    private String toView(final int length) {
        int kb = 0, mb = 0, b = length;
        if (b >= 1024) {
            kb = b / 1024;
            b = b % 1024;
        }
        if (kb >= 1024) {
            mb = kb / 1024;
            kb = kb % 1024;
        }
        if (mb > 0) {
            return String.format("%.2f MB", mb + (kb / 1024.0));
        } else {
            return String.format("%.2f KB", kb + (b / 1024.0));
        }
    }

    // ==================== 数据模型 ====================

    /**
     * 待选择的实况图集会话
     */
    public static class PendingImageSelection {
        public final List<Object> imageList;
        public final byte[] coverBytes;
        public final String audioUrl;
        public final Contact subject;
        public final long senderId;
        public final int total;
        public final long time;

        public PendingImageSelection(List<Object> imageList, byte[] coverBytes, String audioUrl,
                                     Contact subject, long senderId, int total, long time) {
            this.imageList = imageList;
            this.coverBytes = coverBytes;
            this.audioUrl = audioUrl;
            this.subject = subject;
            this.senderId = senderId;
            this.total = total;
            this.time = time;
        }
    }

    @Data
    @Accessors(chain = true)
    public static class JxDataNew {
        private Integer retcode;
        private String retdesc;
        private Boolean succ;
        private NewApiData data;
    }

    @Data
    @Accessors(chain = true)
    public static class NewApiData {
        @JSONField(name = "audio_url")
        private String audioUrl;

        private AuthorInfo author;

        @JSONField(name = "cover_url")
        private String coverUrl;

        @JSONField(name = "image_list")
        private List<Object> imageList;

        private String platform;
        private String title;

        @JSONField(name = "video_id")
        private String videoId;

        @JSONField(name = "video_url")
        private String videoUrl;
    }

    @Data
    @Accessors(chain = true)
    public static class AuthorInfo {
        @JSONField(name = "author_id")
        private String authorId;

        private String avatar;
        private String nickname;
    }
}