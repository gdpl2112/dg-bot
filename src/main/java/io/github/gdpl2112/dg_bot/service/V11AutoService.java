package io.github.gdpl2112.dg_bot.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.MiraiComponent;
import io.github.gdpl2112.dg_bot.dao.V11Conf;
import io.github.gdpl2112.dg_bot.dto.ProfileLike;
import io.github.kloping.date.DateUtils;
import io.github.kloping.judge.Judge;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.SimpleListenerHost;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.util.Date;

/**
 * @author github.kloping
 */
@Slf4j
@Service
public class V11AutoService extends SimpleListenerHost {

    @Autowired
    MiraiComponent component;

    public V11AutoService() {
        super();
    }

    //最后的处理
    @Scheduled(cron = "00 58 23 * * ? ")
    public void autoLike() {
        log.info("最后点赞处理启动");
        for (Bot bot : Bot.getInstances()) {
            if (bot != null && bot.isOnline()) {
                if (bot instanceof RemoteBot) {
                    likeNow(String.valueOf(bot.getId()));
                }
            }
        }
    }

    public String likeNow(String id) {
        StringBuilder sb = null;
        Bot bot = Bot.getInstance(Long.valueOf(id));
        if (bot instanceof RemoteBot) {
            String bid = String.valueOf(bot.getId());
            V11Conf conf = component.userV11Controller.getV11Conf(bid);
            if (!conf.getAutoLike()) return null;
            RemoteBot remoteBot = ((RemoteBot) bot);
            JSONObject jsonData = ProfileLike.getProfileLikeData(remoteBot);

            JSONObject voteInfo = jsonData.getJSONObject("voteInfo");
            JSONArray vUserInfos = voteInfo.getJSONArray("userInfos");

            JSONObject favoriteInfo = jsonData.getJSONObject("favoriteInfo");
            JSONArray fUserInfos = favoriteInfo.getJSONArray("userInfos");

            int dayN = DateUtils.getDay();

            //被点
            for (Object vUserInfo : vUserInfos) {
                ProfileLike pl = new ProfileLike((JSONObject) vUserInfo);
                if (pl.getDay() != dayN) {
                    return sb != null ? sb.toString() : null;
                } else {
                    int max = component.VIP_INFO.get(bot.getId()) ? 20 : 10;
                    if (pl.getBTodayVotedCnt() >= max) continue;
                    if (ProfileLike.sendProfileLike(remoteBot, pl.getVid(), max)) {
                        if (sb == null) sb = new StringBuilder();
                        sb.append("\n(成功)").append("给").append(pl.getVid()).append("点赞").append(max).append("个");
                    }
                }
            }
        }
        return sb != null ? sb.toString() : null;
    }

    // 回赞昨日
    @Scheduled(cron = "00 01 00 * * ?")
    public void yesterday() {
        log.info("回赞昨日启动");
        for (Bot bot : Bot.getInstances()) {
            if (bot != null && bot.isOnline()) {
                yesterdayLieNow(String.valueOf(bot.getId()));
            }
        }
    }

    public String yesterdayLieNow(String id) {
        StringBuilder sb = null;
        Bot bot = Bot.getInstance(Long.valueOf(id));
        if (bot instanceof RemoteBot) {
            int yday = Integer.valueOf(ProfileLike.SF_DD.format(new Date(System.currentTimeMillis() - 1000 * 24 * 60 * 60L)));
            int dayN = DateUtils.getDay();

            String bid = String.valueOf(bot.getId());
            V11Conf conf = component.userV11Controller.getV11Conf(bid);
            if (!conf.getAutoLikeYesterday()) return null;
            RemoteBot remoteBot = ((RemoteBot) bot);
            Boolean isVip = getIsVip(bot.getId(), remoteBot);
            int max = isVip ? 20 : 10;

            JSONObject jsonObject = ProfileLike.getProfileLikeData(remoteBot);
            //被赞回复
            JSONObject voteInfo = jsonObject.getJSONObject("voteInfo");
            JSONArray vUserInfos = voteInfo.getJSONArray("userInfos");
            for (Object vUserInfo : vUserInfos) {
                ProfileLike pl = new ProfileLike((JSONObject) vUserInfo);
                if (pl.getDay() == yday) {
                    if (pl.getBTodayVotedCnt() >= max) continue;
                    if (ProfileLike.sendProfileLike(remoteBot, pl.getVid(), max)) {
                        if (sb == null) sb = new StringBuilder();
                        sb.append("(成功)").append("给").append(pl.getVid()).append("点赞").append(max).append("个");
                    }
                } else if (pl.getDay() == dayN) continue;
                else break;
            }
            //已赞回复
            JSONObject fInfo = jsonObject.getJSONObject("favoriteInfo");
            JSONArray fUserInfos = fInfo.getJSONArray("userInfos");

            for (Object vUserInfo : fUserInfos) {
                ProfileLike pl = new ProfileLike((JSONObject) vUserInfo);
                if (pl.getDay() == yday) {
                    if (pl.getBTodayVotedCnt() >= max) continue;
                    if (ProfileLike.sendProfileLike(remoteBot, pl.getVid(), max)) {
                        if (sb == null) sb = new StringBuilder();
                        sb.append("\n(成功)").append("给").append(pl.getVid()).append("点赞").append(max).append("个");
                    }
                } else if (pl.getDay() == dayN) continue;
                else return sb != null ? sb.toString() : null;
            }
        }
        return sb != null ? sb.toString() : null;
    }

    private Boolean getIsVip(long bid, RemoteBot remoteBot) {
        String data = remoteBot.executeAction("get_stranger_info", "{\"user_id\": \"" + bid + "\"}");
        JSONObject jsonObject = JSONObject.parseObject(data);
        JSONObject jdata = jsonObject.getJSONObject("data");
        Boolean isVip = jdata.getBoolean("is_vip");
        component.VIP_INFO.put(bid, isVip);
        return isVip;
    }

    //自动打卡启动
    @Scheduled(cron = "00 00 00 * * ?")
    public void autoSign() {
        log.info("自动打卡启动");
        for (Bot bot : Bot.getInstances()) {
            if (bot != null && bot.isOnline()) {
                if (bot instanceof RemoteBot) {
                    String bid = String.valueOf(bot.getId());
                    V11Conf conf = component.userV11Controller.getV11Conf(bid);
                    if (Judge.isEmpty(conf.getSignGroups())) return;
                    String groups = conf.getSignGroups();
                    String[] split = groups.split(",|;|\\s");
                    RemoteBot remoteBot = ((RemoteBot) bot);
                    for (String group : split) {
                        String data = remoteBot.executeAction("send_group_sign", String.format(FORMAT_SIGN_DATA, group));
                        JSONObject jsonObject = JSONObject.parseObject(data);
                        if (jsonObject.getInteger("retcode") != 0) {
                            log.error("sign group Failed {} -> b{} g{} o{}", jsonObject, bid, group, groups);
                        } else log.info("自动打卡成功：b{} g{}", bid, group);
                    }
                }
            }
        }
    }

    private static final String FORMAT_SIGN_DATA = "{\"action\": \"send_group_sign\",\"params\": {\"group_id\": \"%s\"}}";
}
