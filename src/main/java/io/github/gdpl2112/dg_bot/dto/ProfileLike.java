package io.github.gdpl2112.dg_bot.dto;

import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.service.ScriptService;
import lombok.Data;
import lombok.experimental.Accessors;
import net.mamoe.mirai.Bot;
import top.mrxiaom.overflow.action.ActionContext;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author github kloping
 * @date 2025/7/17-22:31
 */
@Data
@Accessors(chain = true)
public class ProfileLike {

    public static final SimpleDateFormat SF_DD = new SimpleDateFormat("dd");
    //    public static final SimpleDateFormat SF_MM_DD = new SimpleDateFormat("MM-dd");
    public static final String FORMAT_SEND_LIKE = "{\"user_id\": \"%s\",\"times\": %s}";

    private long vid, date;
    private int count;
    private int day;
    private int bTodayVotedCnt;
    private boolean isSvip;
    private JSONObject odata;

    public ProfileLike(JSONObject fUser) {
        this.odata = fUser;
        vid = fUser.getLong("uin");
        count = fUser.getInteger("count");
        date = fUser.getLong("latestTime") * 1000L;
        day = Integer.valueOf(SF_DD.format(new Date(date)).trim());
        bTodayVotedCnt = fUser.getInteger("bTodayVotedCnt");
        isSvip = fUser.getBoolean("isSvip");
    }


    /**
     * 获得 最近点赞信息 带VIP信息
     *
     * @param remoteBot
     * @return
     */
    public static JSONObject getProfileLikeData1(RemoteBot remoteBot) {
        String data = remoteBot.executeAction(ActionContext.build("get_profile_like", b -> b.throwExceptions(true)), "{}");
        JSONObject jsonObject = JSONObject.parseObject(data);
        jsonObject = jsonObject.getJSONObject("data");
        return jsonObject;
    }

    /**
     * 获得 所有 被点赞的记录  不带VIP信息
     *
     * @param remoteBot
     * @return
     */
    public static JSONObject getProfileLikeData0(RemoteBot remoteBot) {
        String data = remoteBot.executeAction(ActionContext.build("get_profile_like", b -> b.throwExceptions(true)),
                String.format("{\"user_id\": %s}", ((Bot) remoteBot).getId()));
        JSONObject jsonObject = JSONObject.parseObject(data);
        jsonObject = jsonObject.getJSONObject("data");
        return jsonObject;
    }

    // 分页 鞋带VIP信息
    public static JSONObject getProfileLikePage(RemoteBot remoteBot, int st, int count) {
        String data = remoteBot.executeAction(ActionContext.build("get_profile_like", b -> b.throwExceptions(true)),
                String.format("{\"user_id\": 0,\"start\": %s, \"count\": %s}", st, count)
        );
        JSONObject jsonObject = JSONObject.parseObject(data);
        jsonObject = jsonObject.getJSONObject("data");
        return jsonObject;
    }

    public static boolean sendProfileLike(RemoteBot remoteBot, long uin, int times) {
        Bot bot = (Bot) remoteBot;
        ScriptService.offerLogMsg0(String.valueOf(bot.getId()),
                String.format("b%s即将给t%s点赞n%s次", bot.getId(), uin, times));
        String data = remoteBot.executeAction("send_like", String.format(FORMAT_SEND_LIKE, uin, times));
        return "ok".equalsIgnoreCase(JSONObject.parseObject(data).getString("status"));
    }
}
