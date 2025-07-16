package io.github.gdpl2112.dg_bot.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.MiraiComponent;
import io.github.kloping.date.DateUtils;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.SimpleListenerHost;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author github.kloping
 */
@Service
public class AutoLikesService extends SimpleListenerHost {

    private MiraiComponent component;

    public AutoLikesService(MiraiComponent component) {
        super();
        this.component = component;
    }

    private static final String FORMAT_SEND_LIKE = "{\"user_id\": \"%s\",\"times\": %s}";

    private static final SimpleDateFormat SF_DD = new SimpleDateFormat("dd");

    private long upvid = -1L;

    @Scheduled(cron = "0/40 * * * * ? ")
    public void run() {
        for (Bot bot : Bot.getInstances()) {
            if (bot != null) {
                if (bot instanceof RemoteBot) {
                    RemoteBot remoteBot = ((RemoteBot) bot);
                    String data = remoteBot.executeAction("get_profile_like", "{}");
                    JSONObject jsonObject = JSONObject.parseObject(data);

                    jsonObject = jsonObject.getJSONObject("data");

                    JSONObject voteInfo = jsonObject.getJSONObject("voteInfo");
                    JSONArray vUserInfos = voteInfo.getJSONArray("userInfos");
                    JSONObject nvu = vUserInfos.getJSONObject(0);
                    long cvid = nvu.getLong("uin");
                    if (upvid == cvid) return;
                    else upvid = cvid;
                    JSONObject favoriteInfo = jsonObject.getJSONObject("favoriteInfo");
                    JSONArray fUserInfos = favoriteInfo.getJSONArray("userInfos");

                    int dayN = DateUtils.getDay();

                    //已点
                    Map<Long, Integer> f2c = new HashMap<>();
                    for (Object fUserInfo : fUserInfos) {
                        JSONObject fUser = (JSONObject) fUserInfo;
                        Long vid = fUser.getLong("uin");
                        Integer count = fUser.getInteger("count");
                        Long date = fUser.getLong("latestTime") * 1000L;
                        int day = Integer.valueOf(SF_DD.format(new Date(date)).trim());
                        if (day != dayN) {
                            break;
                        } else {
                            f2c.put(vid, count);
                        }
                    }

                    //被点
                    for (Object vUserInfo : vUserInfos) {
                        JSONObject vUser = (JSONObject) vUserInfo;
                        Long vid = vUser.getLong("uin");
                        Integer count = vUser.getInteger("count");
                        Long date = vUser.getLong("latestTime") * 1000L;
                        Integer day = Integer.valueOf(SF_DD.format(new Date(date)).trim());
                        if (day != dayN) {
                            return;
                        } else {
                            int max = component.VIP_INFO.get(bot.getId()) ? 20 : 10;
                            if (f2c.containsKey(vid)) {
                                if (f2c.get(vid) >= max) continue;
                            }
                            remoteBot.executeAction("send_like", String.format(FORMAT_SEND_LIKE, vid, max));
                        }
                    }
                }
            }
        }
    }
}
