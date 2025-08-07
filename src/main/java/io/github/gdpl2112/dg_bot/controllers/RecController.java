package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.MiraiComponent;
import io.github.gdpl2112.dg_bot.dao.LikeReco;
import io.github.gdpl2112.dg_bot.dao.V11Conf;
import io.github.gdpl2112.dg_bot.dto.ProfileLike;
import io.github.gdpl2112.dg_bot.events.ProfileLikeEvent;
import io.github.gdpl2112.dg_bot.events.SendLikedEvent;
import io.github.gdpl2112.dg_bot.mapper.LikeRecoMapper;
import io.github.gdpl2112.dg_bot.service.V11AutoService;
import io.github.kloping.date.DateUtils;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.util.Date;


/**
 * @author github kloping
 * @date 2025/7/17-00:34
 */
@RestController
@RequestMapping("/api/rec")
public class RecController {

    @Autowired
    MiraiComponent component;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @PostMapping
    public void rec(@RequestBody String rdata) {
        JSONObject jo = JSON.parseObject(rdata);
        if ("profile_like".equalsIgnoreCase(jo.getString("sub_type"))) {
            Long tid = jo.getLong("operator_id");
            Long bid = jo.getLong("self_id");
            Integer times = jo.getInteger("times");
            component.log.info(String.format("收到点赞: b%s t%s n%s", bid, tid, times));
            eventPublisher.publishEvent(new ProfileLikeEvent(bid, tid, times));

            int dayN = DateUtils.getDay();
            Bot bot = Bot.getInstanceOrNull(bid);
            V11Conf v11Conf = v11AutoService.getV11Conf(String.valueOf(bot.getId()));

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
                            if (tid.equals(uid)) {
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
                        }
                        return;
                    } else {
                        component.log.info("未找到该用户的点赞信息:" + tid);
                    }
                } catch (Exception e) {
                    component.log.info("收到点赞时获取点赞[列表失败]: " + e.getMessage());
                }
            }
        }
    }

    @Autowired
    V11AutoService v11AutoService;

    @RequestMapping("/test")
    public String test(@RequestParam(name = "id") String bid) {
        try {
            Bot bot = Bot.getInstance(Long.parseLong(bid));
            if (bot instanceof RemoteBot) {
                RemoteBot remoteBot = (RemoteBot) bot;
                return remoteBot.executeAction("get_status", "{}");
            } else return "LOCAL_BOT:" + bot.isOnline();
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
