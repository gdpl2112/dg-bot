package io.github.gdpl2112.dg_bot.dto;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author github kloping
 * @date 2025/7/17-22:31
 */
@Data
@Accessors(chain = true)
@Slf4j
public class ProfileLike {

    public static final SimpleDateFormat SF_DD = new SimpleDateFormat("dd");
    public static final String FORMAT_SEND_LIKE = "{\"user_id\": \"%s\",\"times\": %s}";

    private long vid, date;
    private int count;
    private int day;

    public ProfileLike(JSONObject fUser) {
        vid = fUser.getLong("uin");
        count = fUser.getInteger("count");
        date = fUser.getLong("latestTime") * 1000L;
        day = Integer.valueOf(SF_DD.format(new Date(date)).trim());
    }


    public static JSONObject getProfileLikeData(RemoteBot remoteBot) {
        String data = remoteBot.executeAction("get_profile_like", "{}");
        JSONObject jsonObject = JSONObject.parseObject(data);
        jsonObject = jsonObject.getJSONObject("data");
        return jsonObject;
    }

    public static boolean sendProfileLike(RemoteBot remoteBot, long uin, int times) {
        Bot bot = (Bot) remoteBot;
        log.info("b{}即将给t{}点赞n{}次", bot.getId(), uin, times);
        String data = remoteBot.executeAction("send_like", String.format(FORMAT_SEND_LIKE, uin, times));
        return "ok".equalsIgnoreCase(JSONObject.parseObject(data).getString("status"));
    }
}
