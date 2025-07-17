package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.MiraiComponent;
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

import static io.github.gdpl2112.dg_bot.service.AutoLikesService.FORMAT_SEND_LIKE;
import static io.github.gdpl2112.dg_bot.service.AutoLikesService.SF_DD;

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
                String data = remoteBot.executeAction("get_profile_like", "{}");
                JSONObject jsonObject = JSONObject.parseObject(data);

                jsonObject = jsonObject.getJSONObject("data");
                JSONObject favoriteInfo = jsonObject.getJSONObject("favoriteInfo");
                JSONArray fUserInfos = favoriteInfo.getJSONArray("userInfos");
                int dayN = DateUtils.getDay();
                int max = component.VIP_INFO.get(bot.getId()) ? 20 : 10;
                for (Object fUserInfo : fUserInfos) {
                    JSONObject fUser = (JSONObject) fUserInfo;
                    Long vid = fUser.getLong("uin");
                    Integer count = fUser.getInteger("count");
                    Long date = fUser.getLong("latestTime") * 1000L;
                    int day = Integer.valueOf(SF_DD.format(new Date(date)).trim());
                    if (day != dayN) {
                        break;
                    } else {
                        if (tid.equals(vid)) {
                            if (count < max) {
                                log.info("day{} 已给t{} 点赞{},将进行点赞", day, vid, count);
                                remoteBot.executeAction("send_like", String.format(FORMAT_SEND_LIKE, tid, max));
                            }
                            return;
                        }
                    }
                }
                //第一次点赞
                log.info("day{} 已给t{} 点赞{},将进行点赞", dayN, tid, 0);
                remoteBot.executeAction("send_like", String.format(FORMAT_SEND_LIKE, tid, max));
            }
        }
    }
}
