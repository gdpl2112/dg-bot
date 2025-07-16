package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.MiraiComponent;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mrxiaom.overflow.contact.RemoteBot;

import static io.github.gdpl2112.dg_bot.service.AutoLikesService.FORMAT_SEND_LIKE;

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
    public void rec(@RequestBody String data) {
        JSONObject jo = JSON.parseObject(data);
        //{"time":1752683938,"self_id":3474006766,"post_type":"notice","notice_type":"notify","sub_type":"profile_like","operator_id":291841860,"operator_nick":"skid","times":1}
        if ("profile_like".equalsIgnoreCase(jo.getString("sub_type"))) {
            Long tid = jo.getLong("operator_id");
            Long bid = jo.getLong("self_id");
            Integer times = jo.getInteger("times");
            log.info("收到点赞: b{} t{} n{}",bid,tid,tid);
            Bot bot = Bot.getInstance(bid);
            if (bot instanceof RemoteBot){
                RemoteBot remoteBot = (RemoteBot) bot;
                int max = component.VIP_INFO.get(bot.getId()) ? 20 : 10;
                remoteBot.executeAction("send_like", String.format(FORMAT_SEND_LIKE, tid, max));
            }
        }
    }
}
