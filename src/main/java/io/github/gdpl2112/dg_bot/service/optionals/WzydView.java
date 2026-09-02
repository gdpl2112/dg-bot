package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.dao.AiConf;
import io.github.gdpl2112.dg_bot.mapper.AiConfMapper;
import io.github.kloping.judge.Judge;
import io.github.kloping.number.NumberUtils;
import io.github.kloping.rand.RandomUtils;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author github kloping
 * @since 2025/7/19-20:11
 */
@Slf4j
@Component
public class WzydView implements BaseOptional {

    private static final Path CRY_REVIEW_CACHE_FILE = Path.of("data", "wzyd-cry-reviews.json");
    private static final Object CRY_REVIEW_CACHE_LOCK = new Object();
    private static final long CRY_REVIEW_COMPARE_WINDOW_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final long CRY_REVIEW_RETENTION_MILLIS = Duration.ofDays(1).toMillis();
    private static final int CRY_REVIEW_MAX_ENTRIES = 1000;
    private static final int CRY_REVIEW_MAX_HISTORY_CHARS = 6000;
    private static final int CRY_REVIEW_MAX_SUMMARY_CHARS = 2000;
    private static final int CRY_REVIEW_MAX_OVERVIEW_CHARS = 1800;
    private static final int CRY_REVIEW_MAX_MATCH_LINE_CHARS = 220;
    private static final int CRY_REVIEW_MAX_DIFF_LINES = 8;
    private static Map<String, CryReviewRecord> cryReviewCache;

    private static final ExecutorService AI_CRY_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "wzyd-ai-cry");
        thread.setDaemon(true);
        return thread;
    });

    private static final String AI_CRY_SYSTEM_PROMPT = """
            你是王者荣耀战绩点评助手。请根据用户提供的战绩数据进行准确、简短、幽默但友善的点评，
            覆盖总场次、胜负与胜率、不同模式表现，以及每局的英雄、KDA、评分、MVP、地图和时长等信息.
            其中评分从2~16越高说明越好,偶尔结合用户的常用英雄综合锐评,不要逐局点评,点评的最终字数控制在百字左右.
            只能使用数据中明确给出的内容，不要编造数据；回复使用纯文本，不要使用Markdown格式.
            """;

    private final String[] tips = {
            "数据正在努力绘制中，请稍等片刻哦~",
            "正在搬运你的数据到图像上，请耐心等待~",
            "正在为你整理数据，马上就好啦！",
            "数据洪流正在玩命整理中，请稍等~",
            "正在生成您的专属的图表，请等一下下~",
            "数据正在努力加载中，很快就完成啦！"
    };

    public Map<String, OneApi> apis = new HashMap<>();
    @Value("${wzry.api:null}")
    private String api;

    @Autowired
    private AiConfMapper aiConfMapper;

    {
        OneApi CRY = (content, event) -> {
            long sid = event.getSender().getId();
            String uid = NumberUtils.findNumberFromString(content);
            if (Judge.isNotEmpty(uid))
                // 检测长度 7-10
                if ((uid.length() < 7 || uid.length() > 10)) uid = "";
            for (SingleMessage singleMessage : event.getMessage()) {
                if (singleMessage instanceof At) {
                    At at = (At) singleMessage;
                    sid = at.getTarget();
                }
            }
            content = content.replace("cry", "")
                    .replace("CRY", "")
                    .replace("查荣耀", "");
            content = content.replace(uid, "");
            Object resp = doRequest0("/battle/preview", Map.of("uid", uid, "sid", sid, "opt", content));
            if (resp instanceof Response) {
                try (Response response = (Response) resp) {
                    if (response.isSuccessful()) {
                        MessageChainBuilder mcb = new MessageChainBuilder();
                        mcb.append(new QuoteReply(event.getMessage()));
                        mcb.append(new PlainText(response.body().string()));
                        mcb.append(new PlainText("\n" + getRandomCryTips()));
                        event.getSubject().sendMessage(mcb.build());
                        Object history = doRequest("/battle/history", Map.of("uid", uid, "sid", sid, "opt", content));
                        AiConf aiConf = getAiConf(event);
                        if (isAiCryEnabled(aiConf)) {
                            Object historyText = doRequest("/battle/history/text",
                                    Map.of("uid", uid, "sid", sid, "opt", content));
                            if (historyText instanceof String && isUsableHistoryText((String) historyText)) {
                                submitAiCry(event, aiConf, (String) historyText, content, sid, uid);
                            } else {
                                log.warn("wzry history text unavailable: {}", historyText);
                            }
                        }
                        return history;
                    } else return "请求失败: " + response.body().string();
                } catch (IOException e) {
                    return "请求异常: " + e.getMessage();
                }
            } else return "请求失败: " + resp.toString();
        };
        apis.put("CRY", CRY);
        apis.put("查荣耀", CRY);
        apis.put("当前段位", (content, event) -> {
            long sid = event.getSender().getId();
            String uid = NumberUtils.findNumberFromString(content);
            if (Judge.isNotEmpty(uid))
                if ((uid.length() < 8 || uid.length() > 9)) uid = "";
            for (SingleMessage singleMessage : event.getMessage()) {
                if (singleMessage instanceof At) {
                    At at = (At) singleMessage;
                    sid = at.getTarget();
                }
            }
            return doRequest("/user/", Map.of("uid", uid, "sid", sid));
        });
        apis.put("绑定UID", ((content, event) -> {
            long sid = event.getSender().getId();
            String uid = NumberUtils.findNumberFromString(content);
            if (Judge.isEmpty(uid) && (uid.length() < 8 || uid.length() > 9)) return "请输入正确的UID";
            return doRequest("/bind/", Map.of("uid", uid, "sid", sid));
        }));
        apis.put("删除UID", ((content, event) -> {
            long sid = event.getSender().getId();
            String uid = NumberUtils.findNumberFromString(content);
            if (Judge.isEmpty(uid) && (uid.length() < 8 || uid.length() > 9)) return "请输入正确的UID";
            return doRequest("/bind/un", Map.of("uid", uid, "sid", sid));
        }));
        apis.put("切换UID", ((content, event) -> {
            long sid = event.getSender().getId();
            String uid = NumberUtils.findNumberFromString(content);
            if (Judge.isNotEmpty(uid))
                if (uid.length() < 8 || uid.length() > 9)
                    return "请输入正确的UID";
            return doRequest("/bind/switch", Map.of("uid", uid, "sid", sid));
        }));
        apis.put("当前UID", ((content, event) -> {
            long sid = event.getSender().getId();
            for (SingleMessage singleMessage : event.getMessage()) {
                if (singleMessage instanceof At) {
                    At at = (At) singleMessage;
                    sid = at.getTarget();
                }
            }
            return doRequest("/bind/get", Map.of("sid", sid));
        }));
        apis.put("查询UID", ((content, event) -> {
            String name = content.substring(5);
            if (Judge.isEmpty(name)) return "使用方式: 查询UID\"游戏昵称\" ";
            java.lang.Object data = doRequest("/query/show", Map.of("name", name));
            if (data != null) {
                if (data instanceof String) {
                    try {
                        JSONArray array = JSONArray.parseArray(data.toString());
                        List<UserData> users = array.toJavaList(UserData.class);
                        StringBuilder sb = new StringBuilder("查询结果:\n");
                        for (UserData user : users) {
                            sb.append("----------------\n");
                            sb.append("UID: ").append(user.uid).append("\n")
                                    .append("昵称: ").append(user.name).append("\n")
                                    .append("段位: ").append(user.dw).append("\n")
                                    .append("分区: ").append(user.region).append("\n")
                                    .append("头像: ").append(user.avatar).append("\n");
                        }
                        event.getSubject().sendMessage(sb.toString());
                    } catch (Exception e) {
                        log.error("wzry return String {}", e.getMessage(), e);
                        return data.toString();
                    }
                }
            } else return "未找到该玩家";
            return null;
        }));
    }

    /**
     * 返回随机的 提示语
     *
     * @return 例如: 正在努力为你绘画数据,请耐心等待...
     */
    private String getRandomCryTips() {
        return tips[RandomUtils.RANDOM.nextInt(tips.length)];
    }

    private AiConf getAiConf(MessageEvent event) {
        if (aiConfMapper == null || event == null || event.getBot() == null) return null;
        try {
            // AI配置按机器人账号保存，不能使用战绩接口中的目标 sid 查询。
            return aiConfMapper.selectById(String.valueOf(event.getBot().getId()));
        } catch (Exception e) {
            log.warn("读取AI配置失败", e);
            return null;
        }
    }

    private boolean isAiCryEnabled(AiConf aiConf) {
        return aiConf != null
                && Boolean.TRUE.equals(aiConf.getOpen())
                && Judge.isNotEmpty(aiConf.getApiKey());
    }

    private boolean isUsableHistoryText(String historyText) {
        if (Judge.isEmpty(historyText)) return false;
        return !historyText.startsWith("请求失败:")
                && !historyText.startsWith("请求异常:")
                && !"未配置api".equals(historyText);
    }

    private void submitAiCry(MessageEvent event, AiConf aiConf, String historyText, String opt,
                             long sid, String uid) {
        long now = System.currentTimeMillis();
        String botId = String.valueOf(event.getBot().getId());
        String cacheKey = buildCryReviewCacheKey(botId, sid, uid);
        CryReviewRecord previous = getRecentCryReview(cacheKey, now);
        String prompt = buildAiCryPrompt(historyText, opt, previous, now);
        // 先落盘本次原始战绩，保证短时间内并发查询也能拿到上一份数据进行对比。
        saveCryReview(buildCryReviewRecord(now, botId, sid, uid, opt, historyText,
                previous == null ? "" : previous.review), cacheKey);
        AI_CRY_EXECUTOR.submit(() -> {
            try {
                ChatClient chatClient = AIAssistantOptional.getChatClient(aiConf);
                String answer = chatClient.prompt()
                        .system(AI_CRY_SYSTEM_PROMPT + "\n你的名字是" + aiConf.getName()
                                + "，性格设定是" + aiConf.getTrait() + "。")
                        .user(prompt)
                        .call()
                        .content();
                if (Judge.isEmpty(answer)) return;

                MessageChainBuilder mcb = new MessageChainBuilder();
                mcb.append(new QuoteReply(event.getMessage()));
                String name = Judge.isNotEmpty(aiConf.getName()) ? aiConf.getName() : "AI";
                mcb.append(new PlainText("[" + name + "战绩点评]\n" + answer.trim()));
                event.getSubject().sendMessage(mcb.build());
                saveCryReview(buildCryReviewRecord(now, botId, sid, uid, opt, historyText, answer.trim()), cacheKey);
            } catch (Exception e) {
                log.error("AI cry 调用失败;bid {}", event.getBot().getId(), e);
            }
        });
    }

    private String buildCryReviewCacheKey(String botId, long sid, String uid) {
        return botId + ":" + sid + ":" + (Judge.isNotEmpty(uid) ? uid : "-");
    }

    private CryReviewRecord buildCryReviewRecord(long timestamp, String botId, long sid, String uid,
                                                 String opt, String historyText, String review) {
        return new CryReviewRecord(timestamp, botId, String.valueOf(sid), uid, opt,
                truncate(historyText, CRY_REVIEW_MAX_HISTORY_CHARS),
                truncate(review, CRY_REVIEW_MAX_SUMMARY_CHARS),
                truncate(extractHistoryOverview(historyText), CRY_REVIEW_MAX_OVERVIEW_CHARS),
                extractMatchRecords(historyText));
    }

    private String buildAiCryPrompt(String historyText, String opt, CryReviewRecord previous, long now) {
        String current = truncate(historyText, CRY_REVIEW_MAX_HISTORY_CHARS);
        StringBuilder prompt = new StringBuilder();
        if (previous != null && now - previous.timestamp <= CRY_REVIEW_COMPARE_WINDOW_MILLIS) {
            prompt.append("这是同一账号短时间内的重复查询。请先输出一行不超过80字的“对比总结：”，只概括本次相对上次的变化；\n")
                    .append("然后输出一段简短的“本次锐评：”，重点说明胜率、模式、KDA、评分、MVP和对局时长的变化。\n")
                    .append("上次锐评（仅作上下文）：\n").append(truncate(previous.review, CRY_REVIEW_MAX_SUMMARY_CHARS)).append("\n\n")
                    .append("上次查询模式：").append(Judge.isNotEmpty(previous.opt) ? previous.opt : "全部").append("\n")
                    .append("以下是从最近30局窗口提取出的增量对比（数据已截断）：\n")
                    .append(buildHistoryComparison(previous, historyText)).append("\n\n")
                    .append("本次战绩概览：\n").append(extractHistoryOverview(historyText)).append("\n\n")
                    .append("本次详细战绩（可能已截断）：\n").append(current).append("\n");
        } else {
            prompt.append("请先给出一段极简战绩总结，再给出本次锐评。\n");
            prompt.append("本次战绩数据（可能已截断）：\n").append(current).append("\n");
        }
        prompt.append("本次查询模式：").append(Judge.isNotEmpty(opt) ? opt : "全部");
        return prompt.toString();
    }

    private String buildHistoryComparison(CryReviewRecord previous, String currentHistoryText) {
        Map<String, String> previousMatches = previous.matchRecords;
        if (previousMatches == null || previousMatches.isEmpty()) {
            previousMatches = extractMatchRecords(previous.historyText);
        }
        Map<String, String> currentMatches = extractMatchRecords(currentHistoryText);
        StringBuilder comparison = new StringBuilder();
        String previousOverview = Judge.isNotEmpty(previous.overview)
                ? previous.overview : extractHistoryOverview(previous.historyText);
        String currentOverview = extractHistoryOverview(currentHistoryText);
        if (!previousOverview.equals(currentOverview)) {
            comparison.append("概览变化：\n上次：").append(previousOverview)
                    .append("\n本次：").append(currentOverview).append("\n");
        }
        if (previousMatches.isEmpty() || currentMatches.isEmpty()) {
            return comparison.append("逐局明细无法稳定提取，以下仅参考当前数据：\n")
                    .append(truncate(currentHistoryText, CRY_REVIEW_MAX_HISTORY_CHARS)).toString();
        }

        List<String> added = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, String> entry : currentMatches.entrySet()) {
            String old = previousMatches.get(entry.getKey());
            if (old == null) added.add(entry.getValue());
            else if (!old.equals(entry.getValue())) changed.add(entry.getValue());
        }
        for (Map.Entry<String, String> entry : previousMatches.entrySet()) {
            if (!currentMatches.containsKey(entry.getKey())) removed.add(entry.getValue());
        }
        comparison.append("最近30局窗口：上次").append(previousMatches.size()).append("局，本次")
                .append(currentMatches.size()).append("局；新增").append(added.size())
                .append("局，更新").append(changed.size()).append("局，移出").append(removed.size()).append("局。\n");
        appendMatchDiff(comparison, "新增对局", added);
        appendMatchDiff(comparison, "更新对局", changed);
        appendMatchDiff(comparison, "移出对局", removed);
        if (added.isEmpty() && changed.isEmpty() && removed.isEmpty()) comparison.append("逐局明细未发现变化。\n");
        return comparison.toString();
    }

    private void appendMatchDiff(StringBuilder out, String title, List<String> matches) {
        if (matches.isEmpty()) return;
        out.append(title).append("：\n");
        int limit = Math.min(matches.size(), CRY_REVIEW_MAX_DIFF_LINES);
        for (int i = 0; i < limit; i++) out.append("- ").append(matches.get(i)).append("\n");
        if (matches.size() > limit) out.append("- 其余").append(matches.size() - limit).append("局已省略\n");
    }

    private String extractHistoryOverview(String historyText) {
        if (historyText == null) return "";
        int detailIndex = historyText.indexOf("逐局明细");
        String overview = detailIndex >= 0 ? historyText.substring(0, detailIndex) : historyText;
        return truncate(overview.trim(), CRY_REVIEW_MAX_OVERVIEW_CHARS);
    }

    private Map<String, String> extractMatchRecords(String historyText) {
        Map<String, String> matches = new LinkedHashMap<>();
        if (historyText == null) return matches;
        int detailIndex = historyText.indexOf("逐局明细");
        String details = detailIndex >= 0 ? historyText.substring(detailIndex) : historyText;
        for (String line : details.split("\\R")) {
            String normalized = line.trim().replaceFirst("^\\d+\\.\\s*", "");
            int timeIndex = normalized.indexOf("时间:");
            int separatorIndex = normalized.indexOf('|', timeIndex);
            if (timeIndex < 0 || separatorIndex < 0) continue;
            String time = normalized.substring(timeIndex + 3, separatorIndex).trim();
            if (time.isEmpty()) continue;
            String key = time;
            int duplicate = 2;
            while (matches.containsKey(key)) key = time + "#" + duplicate++;
            matches.put(key, truncate(normalized, CRY_REVIEW_MAX_MATCH_LINE_CHARS));
        }
        return matches;
    }

    private CryReviewRecord getRecentCryReview(String cacheKey, long now) {
        synchronized (CRY_REVIEW_CACHE_LOCK) {
            CryReviewRecord record = getCryReviewCache().get(cacheKey);
            if (record == null || now - record.timestamp > CRY_REVIEW_COMPARE_WINDOW_MILLIS) return null;
            return record;
        }
    }

    private void saveCryReview(CryReviewRecord record, String cacheKey) {
        synchronized (CRY_REVIEW_CACHE_LOCK) {
            Map<String, CryReviewRecord> cache = getCryReviewCache();
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, CryReviewRecord>> iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                CryReviewRecord existing = iterator.next().getValue();
                if (existing == null || now - existing.timestamp > CRY_REVIEW_RETENTION_MILLIS) iterator.remove();
            }
            CryReviewRecord current = cache.get(cacheKey);
            if (current != null && current.timestamp > record.timestamp) return;
            cache.put(cacheKey, record);
            while (cache.size() > CRY_REVIEW_MAX_ENTRIES) {
                String oldestKey = null;
                long oldestTime = Long.MAX_VALUE;
                for (Map.Entry<String, CryReviewRecord> entry : cache.entrySet()) {
                    CryReviewRecord existing = entry.getValue();
                    if (existing != null && existing.timestamp < oldestTime) {
                        oldestKey = entry.getKey();
                        oldestTime = existing.timestamp;
                    }
                }
                if (oldestKey == null) break;
                cache.remove(oldestKey);
            }
            persistCryReviewCache(cache);
        }
    }

    private Map<String, CryReviewRecord> getCryReviewCache() {
        if (cryReviewCache == null) cryReviewCache = loadCryReviewCache();
        return cryReviewCache;
    }

    private Map<String, CryReviewRecord> loadCryReviewCache() {
        Map<String, CryReviewRecord> cache = new HashMap<>();
        try {
            if (!Files.exists(CRY_REVIEW_CACHE_FILE)) return cache;
            JSONObject root = JSON.parseObject(Files.readString(CRY_REVIEW_CACHE_FILE, StandardCharsets.UTF_8));
            if (root == null) return cache;
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                CryReviewRecord record = JSON.parseObject(JSON.toJSONString(entry.getValue()), CryReviewRecord.class);
                if (record != null && record.timestamp > 0) cache.put(entry.getKey(), record);
            }
        } catch (Exception e) {
            log.warn("读取王者AI锐评缓存失败，将使用空缓存", e);
        }
        return cache;
    }

    private void persistCryReviewCache(Map<String, CryReviewRecord> cache) {
        Path tempFile = CRY_REVIEW_CACHE_FILE.resolveSibling(CRY_REVIEW_CACHE_FILE.getFileName() + ".tmp");
        try {
            Path parent = CRY_REVIEW_CACHE_FILE.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(tempFile, JSON.toJSONString(cache), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tempFile, CRY_REVIEW_CACHE_FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, CRY_REVIEW_CACHE_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            log.error("写入王者AI锐评缓存失败", e);
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // ignore cleanup failure
            }
        }
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) return value == null ? "" : value;
        String marker = "\n[数据已截断]\n";
        if (maxChars <= marker.length()) return value.substring(0, maxChars);
        int available = maxChars - marker.length();
        int headChars = (available + 1) / 2;
        int tailChars = available - headChars;
        return value.substring(0, headChars) + marker + value.substring(value.length() - tailChars);
    }

    private static class CryReviewRecord {
        public long timestamp;
        public String botId;
        public String sid;
        public String uid;
        public String opt;
        public String historyText;
        public String review;
        public String overview;
        public Map<String, String> matchRecords;

        public CryReviewRecord() {
        }

        CryReviewRecord(long timestamp, String botId, String sid, String uid, String opt,
                        String historyText, String review, String overview,
                        Map<String, String> matchRecords) {
            this.timestamp = timestamp;
            this.botId = botId;
            this.sid = sid;
            this.uid = uid;
            this.opt = opt;
            this.historyText = historyText;
            this.review = review;
            this.overview = overview;
            this.matchRecords = matchRecords;
        }
    }

    @Override
    public String getDesc() {
        return "通过绑定王者营地UID 查询可视化战绩等数据";
    }

    @Override
    public String getName() {
        return "王者数据可视化";
    }

    @Override
    public void run(MessageEvent event) {
        String out = io.github.gdpl2112.dg_bot.Utils.getLineString(event);
        for (String key : apis.keySet()) {
            if (out.toUpperCase().startsWith(key)) {
                Object data = apis.get(key).run(out, event);
                if (data != null) {
                    MessageChainBuilder mcb = new MessageChainBuilder();
                    mcb.append(new QuoteReply(event.getMessage()));
                    if (data instanceof byte[]) {
                        Image image = Contact.uploadImage(event.getBot().getAsFriend(),
                                new ByteArrayInputStream((byte[]) data));
                        mcb.append(image);
                        event.getSubject().sendMessage(mcb.build());
                    } else if (data instanceof String) {
                        mcb.append(new PlainText((String) data));
                        event.getSubject().sendMessage(mcb.build());
                    } else log.error("wzry return 未知类型: {}", data);
                } else log.error("wzry return null");
                return;
            }
        }
    }

    /**
     * 发起基础请求并返回响应
     *
     * @param url  请求路径
     * @param args 请求参数
     * @return Response 对象或错误信息字符串
     */
    public Object doRequest0(String url, Map<String, Object> args) {
        try {
            if (api == null || "null".equals(api)) return "未配置api";
            StringBuilder urlBuilder = new StringBuilder(api).append(url).append("?");
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                Object value = entry.getValue();
                String valueStr = value != null ? value.toString() : null;
                if (Judge.isNotEmpty(valueStr)) {
                    urlBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                            .append("=")
                            .append(URLEncoder.encode(valueStr, StandardCharsets.UTF_8))
                            .append("&");
                }
            }
            String finalUrl = urlBuilder.toString();
            if (finalUrl.endsWith("&")) finalUrl = finalUrl.substring(0, finalUrl.length() - 1);

            Request request = new Request.Builder()
                    .url(finalUrl)
                    .get()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .build();

            return io.github.gdpl2112.dg_bot.Utils.LONG_TIMEOUT_CLIENT.newCall(request).execute();
        } catch (Exception e) {
            log.error("doRequest0 wzry request error", e);
            return e.getMessage();
        }
    }

    /**
     * 发起请求并根据响应内容类型返回结果
     *
     * @param url  请求路径
     * @param args 请求参数
     * @return 图片字节数组、文本内容或错误信息
     */
    public Object doRequest(String url, Map<String, Object> args) {
        Object resp = doRequest0(url, args);
        if (!(resp instanceof Response)) return resp;
        try (Response response = (Response) resp) {
            if (response.isSuccessful()) {
                String contentType = response.header("Content-Type");
                if (contentType != null && contentType.equalsIgnoreCase("image/png")) {
                    return response.body().bytes();
                } else {
                    return response.body().string();
                }
            } else if (response.code() == 400) {
                return response.body().string();
            } else {
                log.error("wzry return {}\nbody:{}", response.code(), response.body().string());
                return ("请求失败: code." + response.code());
            }
        } catch (Exception e) {
            log.error("wzry request error", e);
            return "请求异常: " + e.getMessage();
        }
    }

    public interface OneApi {
        Object run(String content, MessageEvent event);
    }

    public static class UserData {
        public String uid;
        public String name;
        public String dw;
        public String region;
        public String level;
        public String avatar;
    }
}
