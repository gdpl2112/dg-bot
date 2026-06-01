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

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
            int totalBatches = (totalImages + BATCH_SIZE - 1) / BATCH_SIZE;

            if (totalBatches > 1) {
                builder.append("\n图片数量:").append(String.valueOf(totalImages))
                        .append("/分").append(String.valueOf(totalBatches)).append("批发送,请稍等...");
            } else {
                builder.append("\n图片数量:").append(String.valueOf(totalImages)).append("/正在发送,请稍等...");
            }
            event.getSubject().sendMessage(builder.build());

            for (int batch = 0; batch < totalBatches; batch++) {
                int start = batch * BATCH_SIZE;
                int end = Math.min(start + BATCH_SIZE, totalImages);
                List<Object> batchImages = imageList.subList(start, end);

                var fbuilder = new ForwardMessageBuilder(event.getSubject());
                if (batch == 0 && Judge.isNotEmpty(data.getAudioUrl())) {
                    fbuilder.add(bot, new PlainText("音频直链:" + data.getAudioUrl()));
                }
                if (totalBatches > 1) {
                    fbuilder.add(bot, new PlainText("第" + (batch + 1) + "/" + totalBatches
                            + "批 (图片" + (start + 1) + "-" + end + ")"));
                }

                for (Object imgObj : batchImages) {
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

                        // 发送静态图片 (使用 OkHttp)
                        if (Judge.isNotEmpty(imageUrl)) {
                            try {
                                byte[] bytes = downloadBytesWithOkHttp(imageUrl, DOUYIN_REFERE);
                                Image image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(bytes), "jpg");
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

                                ShortVideo shortVideo = event.getSubject().uploadShortVideo(
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
                event.getSubject().sendMessage(fbuilder.build());
            }

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
     * 使用 OkHttp 下载 URL 内容为字节数组，并设置 Referer
     *
     * @param url       要下载的 URL
     * @param referer   Referer 头部值
     * @return          下载的内容字节数组
     * @throws IOException 如果下载失败
     */
    private byte[] downloadBytesWithOkHttp(String url, String referer) throws IOException {
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