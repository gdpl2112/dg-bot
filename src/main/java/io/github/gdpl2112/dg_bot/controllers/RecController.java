package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.MiraiComponent;
import io.github.gdpl2112.dg_bot.dto.ProfileLike;
import io.github.kloping.date.DateUtils;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.util.Date;


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

    @PostMapping
    public void rec(@RequestBody String rdata) {
        JSONObject jo = JSON.parseObject(rdata);
        //{"time":1752683938,"self_id":3474006766,"post_type":"notice","notice_type":"notify","sub_type":"profile_like","operator_id":291841860,"operator_nick":"skid","times":1}
        if ("profile_like".equalsIgnoreCase(jo.getString("sub_type"))) {
            Long tid = jo.getLong("operator_id");
            Long bid = jo.getLong("self_id");
            Integer times = jo.getInteger("times");
            log.info("收到点赞: b{} t{} n{}", bid, tid, times);
            Bot bot = Bot.getInstance(bid);
            if (bot instanceof RemoteBot) {
                RemoteBot remoteBot = (RemoteBot) bot;
                JSONObject jsonObject = ProfileLike.getProfileLikeData(remoteBot);
                JSONObject favoriteInfo = jsonObject.getJSONObject("favoriteInfo");
                JSONArray fUserInfos = favoriteInfo.getJSONArray("userInfos");
                int dayN = DateUtils.getDay();
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
}
