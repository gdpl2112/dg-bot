package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.MiraiComponent;
import io.github.gdpl2112.dg_bot.dao.LikeReco;
import io.github.gdpl2112.dg_bot.dto.ProfileLike;
import io.github.gdpl2112.dg_bot.mapper.LikeRecoMapper;
import io.github.gdpl2112.dg_bot.service.V11AutoService;
import io.github.kloping.date.DateUtils;
import lombok.Getter;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
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
    LikeRecoMapper likeRecoMapper;

    @Autowired
    MiraiComponent component;

    @PostMapping
    public void rec(@RequestBody String rdata) {
        JSONObject jo = JSON.parseObject(rdata);
        //{"time":1752683938,"self_id":3474006766,"post_type":"notice","notice_type":"notify","sub_type":"profile_like","operator_id":291841860,"operator_nick":"skid","times":1}
        if ("profile_like".equalsIgnoreCase(jo.getString("sub_type"))) {
            Long tid = jo.getLong("operator_id");
            Long bid = jo.getLong("self_id");
            Integer times = jo.getInteger("times");
            component.log.info(String.format("收到点赞: b%s t%s n%s", bid, tid, times));
            int dayN = DateUtils.getDay();
            String date = ProfileLike.SF_MM_DD.format(new Date());
            LikeReco likeReco = likeRecoMapper.getByDateAndBidAndTid(bid, date, tid.toString());
            if (likeReco == null) {
                likeReco = new LikeReco();
                likeReco.setBid(bid.toString());
                likeReco.setTid(tid.toString());
                likeReco.setDate(date);
                likeRecoMapper.insert(likeReco);
            }
            Bot bot = Bot.getInstance(bid);
            if (bot instanceof RemoteBot) {
                RemoteBot remoteBot = (RemoteBot) bot;
                JSONObject jsonObject = ProfileLike.getProfileLikeData(remoteBot);
                JSONObject favoriteInfo = jsonObject.getJSONObject("favoriteInfo");
                JSONArray fUserInfos = favoriteInfo.getJSONArray("userInfos");
                int max = component.VIP_INFO.get(bot.getId()) ? 20 : 10;
                for (Object fUserInfo : fUserInfos) {
                    ProfileLike pl = new ProfileLike((JSONObject) fUserInfo);
                    if (pl.getDay() != dayN) {
                        break;
                    } else {
                        if (tid.equals(pl.getVid())) {
                            if (pl.getCount() < max) {
                                ProfileLike.sendProfileLike(remoteBot, tid, max);
                            }
                            return;
                        }
                    }
                }
                //今日 第一次点赞
                ProfileLike.sendProfileLike(remoteBot, tid, max);
            }
        }
    }

    @Autowired
    V11AutoService v11AutoService;
    @GetMapping("/test")
    public String test() {
        return v11AutoService.yesterdayLieNow("3474006766");
    }
}
