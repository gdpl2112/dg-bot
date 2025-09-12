package io.github.gdpl2112.dg_bot.service.v11s;

import cn.evolvefield.onebot.client.connection.WSGolab;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.MiraiComponent;
import io.github.gdpl2112.dg_bot.controllers.RecController;
import io.github.gdpl2112.dg_bot.dao.V11Conf;
import io.github.gdpl2112.dg_bot.dto.ProfileLike;
import io.github.gdpl2112.dg_bot.events.GroupSignEvent;
import io.github.gdpl2112.dg_bot.events.SendLikedEvent;
import io.github.gdpl2112.dg_bot.mapper.V11ConfMapper;
import io.github.kloping.date.DateUtils;
import io.github.kloping.judge.Judge;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.SimpleListenerHost;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.util.Date;
import java.util.List;

/**
 * @author github.kloping
 */
@Service
public class V11AutoLikeService extends SimpleListenerHost {

    @Autowired
    V11ConfMapper mapper;

    @Autowired
    MiraiComponent component;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public V11AutoLikeService(RecController controller) {
        super();
        WSGolab.INSTANCE.getMsgReceiveList().add(msg -> {
            if (msg.contains("profile_like")) {
                MiraiComponent.EXECUTOR_SERVICE.submit(() -> {
                    JSONObject jo = JSONObject.parseObject(msg);
                    String subType = jo.getString("sub_type");
                    if (subType.equals("profile_like")) {
                        controller.rec(msg);
                    }
                });
                return true;
            }
            return false;
        });
    }

    public @NotNull V11Conf getV11Conf(String id) {
        V11Conf v11Conf = mapper.selectById(id);
        if (v11Conf == null) {
            v11Conf = new V11Conf();
            v11Conf.setQid(id);
            v11Conf.setAutoLike(true);
            v11Conf.setNeedMaxLike(false);
            v11Conf.setAutoZoneLike(false);
            v11Conf.setAutoLikeYesterday(true);
            v11Conf.setLikeBlack("");
            v11Conf.setLikeWhite("");
            v11Conf.setZoneEvl(10);
            v11Conf.setSignGroups("");
            v11Conf.setZoneComment("");
            v11Conf.setZoneWalks("");
            mapper.insert(v11Conf);
        }
        return v11Conf;
    }

    public int updateById(V11Conf v11Conf) {
        return mapper.updateById(v11Conf);
    }
    //最后的处理

    @Scheduled(cron = "00 59 23 * * ? ")
    public void autoLike() {
        component.log.info("最后点赞处理启动");
        for (Bot bot : Bot.getInstances()) {
            if (bot != null && bot.isOnline()) {
                if (bot instanceof RemoteBot) {
                    component.log.info(likeNow(String.valueOf(bot.getId())));
                }
            }
        }
    }

    public String likeNow(String id) {
        StringBuilder sb = null;
        Bot bot = Bot.getInstance(Long.valueOf(id));
        if (bot instanceof RemoteBot) {
            String bid = String.valueOf(bot.getId());
            V11Conf conf = getV11Conf(bid);
            if (!conf.getAutoLike()) return null;
            List<Long> likeBlackIds = conf.getLikeBlackIds();
            int max = component.VIP_INFO.get(bot.getId()) ? 20 : 10;
            RemoteBot remoteBot = ((RemoteBot) bot);
            try {
                int st = 0;
                final int count = 10;
                all:
                while (true) {
                    JSONObject jsonData = ProfileLike.getProfileLikePage(remoteBot, st, count);

                    JSONObject voteInfo = jsonData.getJSONObject("voteInfo");
                    JSONArray vUserInfos = voteInfo.getJSONArray("userInfos");

                    int dayN = DateUtils.getDay();

                    //被点
                    e0:
                    for (Object vUserInfo : vUserInfos) {
                        ProfileLike pl = new ProfileLike((JSONObject) vUserInfo);
                        // 黑名单过滤
                        if (likeBlackIds.contains(pl.getVid())) continue;

                        if (pl.getDay() != dayN) {
                            break all;
                        } else {
                            if (conf.getNeedMaxLike()) {
                                int fmax = pl.isSvip() ? 20 : 10;
                                if (pl.getCount() < fmax) {
                                    continue e0;
                                }
                            }
                            if (pl.getBTodayVotedCnt() >= max) {
                                continue e0;
                            } else {
                                Long vid = pl.getVid();
                                if (ProfileLike.sendProfileLike(remoteBot, vid, max)) {
                                    applicationEventPublisher.publishEvent(new SendLikedEvent(bot.getId(), vid, max, true));
                                    if (sb == null) sb = new StringBuilder();
                                    sb.append("\n(成功)").append("给").append(pl.getVid()).append("点赞").append(max).append("个");
                                }
                            }
                        }
                    }
                    st += count;
                }

                List<Long> likeWhiteIds = conf.getLikeWhiteIds();
                for (Long vid : likeWhiteIds) {
                    if (ProfileLike.sendProfileLike(remoteBot, vid, max)) {
                        applicationEventPublisher.publishEvent(new SendLikedEvent(bot.getId(), vid, max, true));
                        if (sb == null) sb = new StringBuilder();
                        sb.append("\n(成功)").append("给").append(vid).append("点赞").append(max).append("个");
                    }
                }
            } catch (Exception e) {
                component.log.info("day通过ws获得点赞记录失败");
                System.err.println(e.getMessage());
            }

        }
        return sb != null ? sb.toString() : null;
    }

    public static boolean yesterdayLiking = false;

    // 回赞昨日
    @Scheduled(cron = "16 00 00 * * ?")
    public void yesterday() {
        try {
            yesterdayLiking = true;
            component.log.info("回赞昨日启动");
            for (Bot bot : Bot.getInstances()) {
                if (bot != null && bot.isOnline()) {
                    yesterdayLieNow(String.valueOf(bot.getId()));
                }
            }
        } finally {
            yesterdayLiking = false;
            component.log.info("所有回赞结束");
            autoLike();
        }
    }

    public String yesterdayLieNow(String id) {
        StringBuilder sb = null;
        Bot bot = Bot.getInstance(Long.valueOf(id));
        if (bot instanceof RemoteBot) {
            int yday = Integer.valueOf(ProfileLike.SF_DD.format(new Date(System.currentTimeMillis() - 1000 * 24 * 60 * 60L)));
            int dayN = DateUtils.getDay();

            String bid = String.valueOf(bot.getId());
            V11Conf conf = getV11Conf(bid);
            if (!conf.getAutoLikeYesterday()) return null;
            RemoteBot remoteBot = ((RemoteBot) bot);
            Boolean isVip = getIsVip(bot.getId(), remoteBot);
            int max = isVip ? 20 : 10;

            int st = 0;
            final int count = 10;
            all:
            while (true) {
                try {
                    JSONObject jsonObject = ProfileLike.getProfileLikePage(remoteBot, st, count);
                    JSONObject voteInfo = jsonObject.getJSONObject("voteInfo");
                    JSONArray vUserInfos = voteInfo.getJSONArray("userInfos");
                    e:
                    for (Object vUserInfo : vUserInfos) {
                        ProfileLike pl = new ProfileLike((JSONObject) vUserInfo);
                        if (pl.getDay() == yday) {
                            if (conf.getNeedMaxLike()) {
                                int fmax = pl.isSvip() ? 20 : 10;
                                if (pl.getCount() < fmax) continue e;
                            }
                            if (pl.getBTodayVotedCnt() >= max) continue e;
                            if (ProfileLike.sendProfileLike(remoteBot, pl.getVid(), max)) {
                                applicationEventPublisher.publishEvent(new SendLikedEvent(bot.getId(), pl.getVid(), max, true));
                                if (sb == null) sb = new StringBuilder();
                                sb.append("\n(成功)").append("给").append(pl.getVid()).append("点赞").append(max).append("个");
                            }
                        } else if (pl.getDay() == dayN) continue e;
                        else break all;
                    }
                } catch (Exception e) {
                    component.log.info("通过ws获得点赞记录失败");
                    System.err.println(e.getMessage());
                    break all;
                }
                st += count;
            }
        }
        return sb != null ? sb.toString() : null;
    }

    public Boolean getIsVip(long bid, RemoteBot remoteBot) {
        String data = remoteBot.executeAction("get_stranger_info", "{\"user_id\": \"" + bid + "\"}");
        JSONObject jsonObject = JSONObject.parseObject(data);
        JSONObject jdata = jsonObject.getJSONObject("data");
        Boolean isVip = jdata.getBoolean("is_vip");
        component.VIP_INFO.put(bid, isVip);
        return isVip;
    }

    @Scheduled(cron = "01 00 00 * * ?")
    //自动打卡启动
    public void autoSign() {
        component.log.info("自动打卡启动");
        for (Bot bot : Bot.getInstances()) {
            signNow(bot);
        }
    }

    public void signNow(Bot bot) {
        MiraiComponent.EXECUTOR_SERVICE.submit(() -> {
            if (bot != null && bot.isOnline()) {
                if (bot instanceof RemoteBot) {
                    String bid = String.valueOf(bot.getId());
                    V11Conf conf = getV11Conf(bid);
                    component.log.info("自动打卡: " + bid + " conf-sign: " + conf.getSignGroups());
                    if (Judge.isEmpty(conf.getSignGroups())) return;
                    String groups = conf.getSignGroups();
                    RemoteBot remoteBot = ((RemoteBot) bot);
                    for (Long gid : conf.getSignGroupIds()) {
                        String data0 = "{\"group_id\": \"" + gid + "\"}";
                        String data = remoteBot.executeAction("send_group_sign", data0);
                        JSONObject jsonObject = JSONObject.parseObject(data);
                        if (jsonObject.getInteger("retcode") != 0) {
                            component.log.error(String.format("sign group Failed %s -> b%s g%s o%s", jsonObject, bid, gid, groups));
                        } else {
                            component.log.info("自动打卡成功：b" + bid + " g" + gid);
                            applicationEventPublisher.publishEvent(new GroupSignEvent(gid, bot.getId(), bot.getId(), true));
                        }
                    }
                }
            }
        });
    }

    private static final String FORMAT_SIGN_DATA = "{\"action\": \"send_group_sign\",\"params\": {\"group_id\": \"%s\"}}";
}
