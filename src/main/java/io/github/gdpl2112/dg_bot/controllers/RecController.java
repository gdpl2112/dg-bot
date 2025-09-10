package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.MiraiComponent;
import io.github.gdpl2112.dg_bot.dao.V11Conf;
import io.github.gdpl2112.dg_bot.dto.ProfileLike;
import io.github.gdpl2112.dg_bot.events.ProfileLikeEvent;
import io.github.gdpl2112.dg_bot.events.SendLikedEvent;
import io.github.gdpl2112.dg_bot.service.ScriptService;
import io.github.gdpl2112.dg_bot.service.v11s.V11AutoLikeService;
import io.github.gdpl2112.dg_bot.service.v11s.V11QzoneService;
import io.github.kloping.date.DateUtils;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.*;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * @author github kloping
 * @date 2025/7/17-00:34
 */
@Slf4j
@RestController
@RequestMapping("/api/rec")
public class RecController {

    @Autowired
    MiraiComponent component;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    private Map<String, CountDownLatch> key2cdl = new ConcurrentHashMap<>();

    @PostMapping
    public void rec(@RequestBody String rdata) {
        if (V11AutoLikeService.yesterdayLiking) {
            component.log.info("回赞昨日进行中..忽略点赞");
            return;
        }
        JSONObject jo = JSON.parseObject(rdata);
        if ("profile_like".equalsIgnoreCase(jo.getString("sub_type"))) {
            Long tid = jo.getLong("operator_id");
            Long bid = jo.getLong("self_id");

            // 控制短时内重读点赞
            String key = String.format("b%s_t%s", bid, tid);
            try {
                CountDownLatch cdl = null;
                if (key2cdl.containsKey(key)) {
                    cdl = key2cdl.get(key);
                    if (cdl != null) cdl.countDown();
                    key2cdl.put(key, cdl = new CountDownLatch(1));
                } else {
                    key2cdl.put(key, cdl = new CountDownLatch(1));
                }
                boolean k = cdl.await(333, TimeUnit.MILLISECONDS);
                if (k) {
                    component.log.info("已忽略重复: " + key);
                    return;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 开始处理
            Integer times = jo.getInteger("times");
            ScriptService.offerLogMsg0(bid.toString(), String.format("收到点赞id:%s=%s次", tid, times));
            eventPublisher.publishEvent(new ProfileLikeEvent(bid, tid, times));

            int dayN = DateUtils.getDay();
            Bot bot = Bot.getInstanceOrNull(bid);
            if (bot == null) {
                component.log.error("BOT b" + bot.getId() + "未初始化完成!");
                return;
            }
            V11Conf v11Conf = v11AutoLikeService.getV11Conf(String.valueOf(bot.getId()));
            //黑名单过滤
            List<Long> likeBlackIds = v11Conf.getLikeBlackIds();
            if (likeBlackIds.contains(tid)) return;

            if (bot != null && bot instanceof RemoteBot) {
                RemoteBot remoteBot = null;
                try {
                    remoteBot = (RemoteBot) bot;
                    JSONObject jsonObject = ProfileLike.getProfileLikeData1(remoteBot);
                    JSONObject infoObj = jsonObject.getJSONObject("voteInfo");
                    JSONArray infos = infoObj.getJSONArray("userInfos");
                    ProfileLike pl = null;
                    for (Object info : infos) {
                        JSONObject info0 = (JSONObject) info;
                        long cut = info0.getLong("latestTime") * 1000L;
                        int day = Integer.valueOf(ProfileLike.SF_DD.format(new Date(cut)).trim());
                        long uid = info0.getLong("uin");
                        if (day != dayN) {
                            break;
                        } else {
                            if (tid == uid) {
                                pl = new ProfileLike(info0);
                                break;
                            }
                        }
                    }
                    if (pl != null) {
                        if (v11Conf.getNeedMaxLike()) {
                            int fmax = pl.isSvip() ? 20 : 10;
                            if (pl.getCount() < fmax) {
                                component.log.waring(String.format("B%s开启满赞回赞,当前f%s点赞%s次,返回", bid, pl.getVid(), pl.getCount()));
                                return;
                            }
                        }
                        // 保存点赞信息
                        // v11AutoService.saveLikeReco(bid, date, tid);
                        //如果没点满 则点赞
                        int max = component.VIP_INFO.get(bot.getId()) ? 20 : 10;
                        if (pl.getBTodayVotedCnt() < max) {
                            Boolean ok = ProfileLike.sendProfileLike(remoteBot, tid, max);
                            eventPublisher.publishEvent(new SendLikedEvent(bid, tid, max, ok));
                        } else component.log.waring("跳过b" + bid + "已满赞t" + tid + "C:" + pl.getBTodayVotedCnt());
                        return;
                    } else {
                        component.log.info("未找到该用户的点赞信息:" + tid);
                    }
                } catch (Exception e) {
                    String msg = "收到点赞时获取点赞[列表失败]: " + e.getMessage();
                    component.log.error(msg);
                    ScriptService.offerLogMsg0(bid.toString(), msg);
                }
            }
            key2cdl.remove(key);
        }
    }

    @Autowired
    @Lazy
    V11AutoLikeService v11AutoLikeService;

    @Autowired
    @Lazy
    V11QzoneService v11QzoneService;

    @RequestMapping("/test")
    public Object test(@RequestParam(name = "id") String bid) {
//        Long bid0 = Long.parseLong(bid);
//        try {
//            RemoteBot bot = (RemoteBot) Bot.getInstanceOrNull(bid0);
//            v11QzoneService.startQzoneWalkNow(bid0, bot);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return e.getMessage();
//        }
//        try {
//            Bot bot = Bot.getInstance(Long.parseLong(bid));
//            if (bot instanceof RemoteBot) {
//                RemoteBot remoteBot = (RemoteBot) bot;
//                return remoteBot.executeAction("get_status", "{}");
//            } else return "LOCAL_BOT:" + bot.isOnline();
//        } catch (Exception e) {
//            return e.getMessage();
//        }
        return "ok";
    }
}
