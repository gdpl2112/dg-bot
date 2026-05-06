package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSONArray;
import io.github.kloping.judge.Judge;
import io.github.kloping.number.NumberUtils;
import io.github.kloping.rand.RandomUtils;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author github kloping
 * @since 2025/7/19-20:11
 */
@Slf4j
//@Component
public class WzydView implements BaseOptional {

    public Map<String, OneApi> apis = new HashMap<>();

    public interface OneApi {
        Object run(String content, MessageEvent event);
    }

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
                        return doRequest("/battle/history", Map.of("uid", uid, "sid", sid, "opt", content));
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

    private final String[] tips = {
            "数据正在努力绘制中，请稍等片刻哦~",
            "正在搬运你的数据到图像上，请耐心等待~",
            "正在为你整理数据，马上就好啦！",
            "数据洪流正在玩命整理中，请稍等~",
            "正在生成您的专属的图表，请等一下下~",
            "数据正在努力加载中，很快就完成啦！"
    };

    public static class UserData {
        public String uid;
        public String name;
        public String dw;
        public String region;
        public String level;
        public String avatar;
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
                    }else log.error("wzry return 未知类型: {}", data);
                } else log.error("wzry return null");
                return;
            }
        }
    }

    @Value("${wzry.api:null}")
    private String api;

    private static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
            .build();

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
                    urlBuilder.append(entry.getKey()).append("=").append(valueStr).append("&");
                }
            }
            String finalUrl = urlBuilder.toString();
            if (finalUrl.endsWith("&")) finalUrl = finalUrl.substring(0, finalUrl.length() - 1);

            Request request = new Request.Builder()
                    .url(finalUrl)
                    .get()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .build();

            return OK_HTTP_CLIENT.newCall(request).execute();
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
}
