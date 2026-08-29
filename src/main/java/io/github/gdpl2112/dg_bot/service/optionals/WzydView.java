package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSONArray;
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
import java.util.HashMap;
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

    private static final ExecutorService AI_CRY_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "wzyd-ai-cry");
        thread.setDaemon(true);
        return thread;
    });

    private static final String AI_CRY_SYSTEM_PROMPT = """
            你是王者荣耀战绩点评助手。请根据用户提供的战绩数据进行准确、简短、幽默但友善的点评，
            覆盖总场次、胜负与胜率、不同模式表现，以及每局的英雄、KDA、评分、MVP、地图和时长等信息.
            其中评分从2~16越高说明越好,结合用户的常用英雄综合锐评,点评的最终文字不要太冗长,不要逐局点评,可以突出点评某几局.
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
                                submitAiCry(event, aiConf, (String) historyText, content);
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

    private void submitAiCry(MessageEvent event, AiConf aiConf, String historyText, String opt) {
        String prompt = "请点评以下王者荣耀战绩" + (Judge.isNotEmpty(opt) ? "（查询模式：" + opt + "）" : "")
                + "：\n\n" + historyText;
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
            } catch (Exception e) {
                log.error("AI cry 调用失败;bid {}", event.getBot().getId(), e);
            }
        });
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
