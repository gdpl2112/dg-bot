package io.github.gdpl2112.dg_bot.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.MiraiComponent;
import io.github.gdpl2112.dg_bot.dao.LikeReco;
import io.github.gdpl2112.dg_bot.dao.V11Conf;
import io.github.gdpl2112.dg_bot.dto.ProfileLike;
import io.github.gdpl2112.dg_bot.events.GroupSignEvent;
import io.github.gdpl2112.dg_bot.events.SendLikedEvent;
import io.github.gdpl2112.dg_bot.mapper.LikeRecoMapper;
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
import java.util.LinkedList;
import java.util.List;

/**
 * @author github.kloping
 */
@Service
public class V11AutoService extends SimpleListenerHost {

    @Autowired
    V11ConfMapper mapper;

    @Autowired
    MiraiComponent component;

    @Autowired
    private LikeRecoMapper likeRecoMapper;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public V11AutoService() {
        super();
    }

    public @NotNull V11Conf getV11Conf(String id) {
        V11Conf v11Conf = mapper.selectById(id);
        if (v11Conf == null) {
            v11Conf = new V11Conf();
            v11Conf.setQid(id);
            v11Conf.setAutoLike(true);
            v11Conf.setAutoZoneLike(true);
            v11Conf.setAutoLikeYesterday(true);
            v11Conf.setSignGroups("");
            v11Conf.setZoneComment("");
            mapper.insert(v11Conf);
        }
        return v11Conf;
    }

    public int updateById(V11Conf v11Conf) {
        return mapper.updateById(v11Conf);
    }
    //最后的处理

    @Scheduled(cron = "00 58 23 * * ? ")
    public void autoLike() {
        component.log.info("最后点赞处理启动");
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
            V11Conf conf = getV11Conf(bid);
            if (!conf.getAutoLike()) return null;
            RemoteBot remoteBot = ((RemoteBot) bot);
            JSONObject jsonData = ProfileLike.getProfileLikeData(remoteBot);

            JSONObject voteInfo = jsonData.getJSONObject("voteInfo");
            JSONArray vUserInfos = voteInfo.getJSONArray("userInfos");

            JSONObject favoriteInfo = jsonData.getJSONObject("favoriteInfo");
            JSONArray fUserInfos = favoriteInfo.getJSONArray("userInfos");

            int dayN = DateUtils.getDay();

            String date = ProfileLike.SF_MM_DD.format(new Date());
            List<LikeReco> list = likeRecoMapper.selectListByDateAndBid(bid, date);
            component.log.info("今日记录: " + bid + "-> " + list);
            int max = component.VIP_INFO.get(bot.getId()) ? 20 : 10;
            //被点
            for (Object vUserInfo : vUserInfos) {
                ProfileLike pl = new ProfileLike((JSONObject) vUserInfo);
                if (pl.getDay() != dayN) {
                    break;
                } else {
                    if (pl.getBTodayVotedCnt() >= max) {
                        list.removeIf(l -> l.getTid().equals(pl.getVid()));
                        continue;
                    }
                    Long vid = pl.getVid();
                    if (ProfileLike.sendProfileLike(remoteBot, vid, max)) {
                        applicationEventPublisher.publishEvent(new SendLikedEvent(bot.getId(), vid, max, true));
                        if (sb == null) sb = new StringBuilder();
                        sb.append("\n(成功)").append("给").append(pl.getVid()).append("点赞").append(max).append("个");
                        list.removeIf(l -> l.getTid().equals(pl.getVid()));
                    }
                }
            }
            if (!list.isEmpty()){
                for (LikeReco pl : list) {
                    Long tid = Long.parseLong(pl.getTid());
                    if (ProfileLike.sendProfileLike(remoteBot, tid, max)) {
                        applicationEventPublisher.publishEvent(new SendLikedEvent(bot.getId(), tid, max, true));
                        if (sb == null) sb = new StringBuilder();
                        sb.append("\n'遗漏'(成功)").append("给").append(pl.getTid()).append("点赞").append(max).append("个");
                    }
                }
            }
        }
        return sb != null ? sb.toString() : null;
    }

    // 回赞昨日
    @Scheduled(cron = "00 01 00 * * ?")
    public void yesterday() {
        component.log.info("回赞昨日启动");
        for (Bot bot : Bot.getInstances()) {
            if (bot != null && bot.isOnline()) {
                yesterdayLieNow(String.valueOf(bot.getId()));
            }
        }
        component.log.info("回赞结束 删除全部记录: " + likeRecoMapper.delete(null));
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

            JSONObject jsonObject = ProfileLike.getProfileLikeData(remoteBot);
            //被赞回复
            JSONObject voteInfo = jsonObject.getJSONObject("voteInfo");
            JSONArray vUserInfos = voteInfo.getJSONArray("userInfos");
            List<Long> vls = new LinkedList<>();
            for (Object vUserInfo : vUserInfos) {
                ProfileLike pl = new ProfileLike((JSONObject) vUserInfo);
                if (pl.getDay() == yday) {
                    if (pl.getBTodayVotedCnt() >= max) continue;
                    vls.add(pl.getVid());
                    if (ProfileLike.sendProfileLike(remoteBot, pl.getVid(), max)) {
                        applicationEventPublisher.publishEvent(new SendLikedEvent(bot.getId(), pl.getVid(), max, true));
                        if (sb == null) sb = new StringBuilder();
                        sb.append("\n(成功)").append("给").append(pl.getVid()).append("点赞").append(max).append("个");
                    }
                } else if (pl.getDay() == dayN) continue;
                else break;
            }
            //==== 根据记录查询
            String date = ProfileLike.SF_MM_DD.format(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24));
            List<LikeReco> list = likeRecoMapper.selectListByDateAndBid(bid, date);
            component.log.info("昨日记录: " + bid + "-> " + list);
            for (LikeReco likeReco : list) {
                if (vls.contains(likeReco.getTid())) continue;
                Long tid = Long.parseLong(likeReco.getTid());
                if (ProfileLike.sendProfileLike(remoteBot, tid, max)) {
                    applicationEventPublisher.publishEvent(new SendLikedEvent(bot.getId(), tid, max, true));
                    if (sb == null) sb = new StringBuilder();
                    sb.append("\n(成功)").append("给").append(likeReco.getTid()).append("点赞").append(max).append("个");
                }
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

    @Scheduled(cron = "01 00 00 * * ?")
    public void autoSign() {
        component.log.info("自动打卡启动");
        for (Bot bot : Bot.getInstances()) {
            if (bot != null && bot.isOnline()) {
                if (bot instanceof RemoteBot) {
                    String bid = String.valueOf(bot.getId());
                    V11Conf conf = getV11Conf(bid);
                    component.log.info("自动打卡: " + bid + " conf-sign: " + conf.getSignGroups());
                    if (Judge.isEmpty(conf.getSignGroups())) continue;
                    String groups = conf.getSignGroups();
                    String[] split = groups.split(",|;|\\s");
                    RemoteBot remoteBot = ((RemoteBot) bot);
                    for (String group : split) {
                        if (Judge.isEmpty(group)) continue;
                        Long gid = Long.parseLong(group);
                        String data0 = "{\"group_id\": \"" + gid + "\"}";
                        String data = remoteBot.executeAction("send_group_sign", data0);
                        JSONObject jsonObject = JSONObject.parseObject(data);
                        if (jsonObject.getInteger("retcode") != 0) {
                            component.log.error(String.format("sign group Failed %s -> b%s g%s o%s", jsonObject, bid, group, groups));
                        } else {
                            component.log.info("自动打卡成功：b" + bid + " g" + group);
                            applicationEventPublisher.publishEvent(new GroupSignEvent(gid,bot.getId() , true));
                        }
                    }
                }
            }
        }
    }

    private static final String FORMAT_SIGN_DATA = "{\"action\": \"send_group_sign\",\"params\": {\"group_id\": \"%s\"}}";
}
