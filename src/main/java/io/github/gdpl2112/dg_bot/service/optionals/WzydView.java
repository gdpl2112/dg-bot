package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSONArray;
import io.github.kloping.judge.Judge;
import io.github.kloping.number.NumberUtils;
import io.github.kloping.rand.RandomUtils;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author github kloping
 * @since 2025/7/19-20:11
 */
@Slf4j
@Component
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
            if (resp instanceof Connection.Response) {
                Connection.Response response = (Connection.Response) resp;
                if (response.statusCode() == 200) {
                    MessageChainBuilder mcb = new MessageChainBuilder();
                    mcb.append(new QuoteReply(event.getMessage()));
                    mcb.append(new PlainText(response.body()));
                    mcb.append(new PlainText("\n" + getRandomCryTips()));
                    event.getSubject().sendMessage(mcb.build());
                    return doRequest("/battle/history", Map.of("uid", uid, "sid", sid, "opt", content));
                } else return "请求失败: " + response.body();
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

    public Object doRequest0(String url, Map<String, Object> args) {
        try {
            if (api == null || "null".equals(api)) return "未配置api";
            url = api + url + "?";
            for (String key0 : args.keySet()) {
                Object value = args.get(key0);
                String value0 = value != null ? value.toString() : null;
                if (Judge.isNotEmpty(value0)) {
                    url += key0 + "=" + args.get(key0) + "&";
                }
            }
            if (url.endsWith("&")) url = url.substring(0, url.length() - 1);
            Connection.Response response = Jsoup.connect(url).ignoreHttpErrors(true)
                    .ignoreContentType(true).method(Connection.Method.GET).timeout(90 * 1000).execute();
            return response;
        } catch (Exception e) {
            log.error("doRequest0 wzry request error", e);
            return e.getMessage();
        }
    }

    public Object doRequest(String url, Map<String, Object> args) {
        try {
            if (api == null || "null".equals(api)) return "未配置api";
            url = api + url + "?";
            for (String key0 : args.keySet()) {
                Object value = args.get(key0);
                String value0 = value != null ? value.toString() : null;
                if (Judge.isNotEmpty(value0)) {
                    url += key0 + "=" + args.get(key0) + "&";
                }
            }
            if (url.endsWith("&")) url = url.substring(0, url.length() - 1);
            Connection.Response response = Jsoup.connect(url).ignoreHttpErrors(true)
                    .ignoreContentType(true).method(Connection.Method.GET).timeout(90 * 1000).execute();
            if (response.statusCode() == 200) {
                if (response.contentType().equalsIgnoreCase("image/png")) {
                    return response.bodyAsBytes();
                } else {
                    return response.body();
                }
            } else if (response.statusCode() == 400) {
                return response.body();
            } else {
                log.error("wzry return {}\nbody:{}", response.statusCode(), response.body());
                return ("请求失败: code." + response.statusCode());
            }
        } catch (Exception e) {
            log.error("wzry request error", e);
            return "请求异常: " + e.getMessage();
        }
    }
}
