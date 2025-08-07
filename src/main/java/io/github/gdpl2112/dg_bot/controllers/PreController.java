package io.github.gdpl2112.dg_bot.controllers;

import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.dto.BotInfo;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * @author github kloping
 * @date 2025/6/2-22:06
 */
@RestController
@RequestMapping("/api")
public class PreController {

    @Autowired
    AuthMapper authMapper;

    @GetMapping("/bot/list")
    public List<BotInfo> lists() {
        List<AuthM> auths = authMapper.selectList(null);
        List<BotInfo> infos = new ArrayList<>();
        for (AuthM auth : auths) {
            BotInfo botInfo;
            Bot bot = Bot.getInstanceOrNull(Long.valueOf(auth.getQid()));
            if (bot != null) {
                botInfo = new BotInfo()
                        .setId(bot.getId())
                        .setNick(bot.getNick())
                        .setAvatar(bot.getAvatarUrl())
                        .setOnline(bot.isOnline());
            } else {
                botInfo = new BotInfo()
                        .setId(Long.valueOf(auth.getQid()))
                        .setNick("离线")
                        .setAvatar("https://q1.qlogo.cn/g?b=qq&nk=" + auth.getQid() + "&s=640")
                        .setOnline(false);
            }
            infos.add(botInfo);
        }
        return infos;
    }
}
