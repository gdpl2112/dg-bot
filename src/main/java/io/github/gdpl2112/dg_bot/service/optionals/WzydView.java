package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSONArray;
import io.github.kloping.judge.Judge;
import io.github.kloping.number.NumberUtils;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.SingleMessage;
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
                if ((uid.length() < 8 || uid.length() > 9)) uid = "";
            for (SingleMessage singleMessage : event.getMessage()) {
                if (singleMessage instanceof At) {
                    At at = (At) singleMessage;
                    sid = at.getTarget();
                }
            }
            content = content.replace("cry", "").replace("查荣耀", "");
            content = content.replace(uid, "");
            return doRequest("/battle/history", Map.of("uid", uid, "sid", sid, "opt", content));
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
                    if (data instanceof byte[]) {
                        Image image = Contact.uploadImage(event.getBot().getAsFriend(),
                                new ByteArrayInputStream((byte[]) data));
                        event.getSubject().sendMessage(image);
                    } else if (data instanceof String) {
                        event.getSubject().sendMessage((String) data);
                    }
                    System.err.println("wzry return 未知类型: " + data);
                }
                return;
            }
        }
    }

    @Value("${wzry.api:null}")
    private String api;

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
            } else {
                System.err.println("wzry return " + response.statusCode() + "\nbody:" + response.body());
                return ("请求失败: code." + response.statusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "请求异常: " + e.getMessage();
        }
    }
}
