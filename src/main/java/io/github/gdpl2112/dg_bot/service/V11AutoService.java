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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author github.kloping
 */
@Slf4j
@Service
public class V11AutoService extends SimpleListenerHost {

    private MiraiComponent component;

    public V11AutoService(MiraiComponent component) {
        super();
        this.component = component;
    }

    //最后的处理
    @Scheduled(cron = "00 58 23 * * ? ")
    public void run() {
        log.info("最后点赞处理启动");
        for (Bot bot : Bot.getInstances()) {
            if (bot != null && bot.isOnline()) {
                if (bot instanceof RemoteBot) {
                    String bid = String.valueOf(bot.getId());
                    V11Conf conf = component.userV11Controller.getV11Conf(bid);
                    if (!conf.getAutoLike()) return;
                    RemoteBot remoteBot = ((RemoteBot) bot);
                    JSONObject jsonData = ProfileLike.getProfileLikeData(remoteBot);

                    JSONObject voteInfo = jsonData.getJSONObject("voteInfo");
                    JSONArray vUserInfos = voteInfo.getJSONArray("userInfos");

                    JSONObject favoriteInfo = jsonData.getJSONObject("favoriteInfo");
                    JSONArray fUserInfos = favoriteInfo.getJSONArray("userInfos");

                    int dayN = DateUtils.getDay();
                    //已点
                    Map<Long, Integer> f2c = new HashMap<>();
                    for (Object fUserInfo : fUserInfos) {
                        ProfileLike pl = new ProfileLike((JSONObject) fUserInfo);
                        if (pl.getDay() != dayN) {
                            break;
                        } else {
                            f2c.put(pl.getVid(), pl.getCount());
                        }
                    }

                    //被点
                    for (Object vUserInfo : vUserInfos) {
                        ProfileLike pl = new ProfileLike((JSONObject) vUserInfo);
                        if (pl.getDay() != dayN) {
                            return;
                        } else {
                            int max = component.VIP_INFO.get(bot.getId()) ? 20 : 10;
                            if (f2c.containsKey(pl.getVid())) {
                                if (f2c.get(pl.getVid()) >= max) continue;
                            }
                            ProfileLike.sendProfileLike(remoteBot, pl.getVid(), max);
                        }
                    }
                }
            }
        }
    }

    // 回赞昨日
    @Scheduled(cron = "00 01 00 * * ?")
    public void yesterday() {
        log.info("回赞昨日启动");
        int yday = Integer.valueOf(ProfileLike.SF_DD.format(new Date(System.currentTimeMillis() - 1000 * 24 * 60 * 60L)));
        int dayN = DateUtils.getDay();
        for (Bot bot : Bot.getInstances()) {
            if (bot != null && bot.isOnline()) {
                if (bot instanceof RemoteBot) {
                    String bid = String.valueOf(bot.getId());
                    V11Conf conf = component.userV11Controller.getV11Conf(bid);
                    if (!conf.getAutoLikeYesterday()) return;

                    RemoteBot remoteBot = ((RemoteBot) bot);
                    JSONObject jsonObject = ProfileLike.getProfileLikeData(remoteBot);

                    JSONObject voteInfo = jsonObject.getJSONObject("voteInfo");
                    JSONArray vUserInfos = voteInfo.getJSONArray("userInfos");

                    Boolean isVip = getIsVip(bot.getId(), remoteBot);
                    int max = isVip ? 20 : 10;
                    for (Object vUserInfo : vUserInfos) {
                        ProfileLike pl = new ProfileLike((JSONObject) vUserInfo);
                        if (pl.getDay() == yday) ProfileLike.sendProfileLike(remoteBot, pl.getVid(), max);
                        else if (pl.getDay() == dayN) continue;
                        else return;
                    }
                }
            }
        }
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
