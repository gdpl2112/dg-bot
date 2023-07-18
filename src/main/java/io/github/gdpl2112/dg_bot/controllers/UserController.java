package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.kloping.judge.Judge;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author github.kloping
 */
@RestController
public class UserController {
    @Autowired
    AuthMapper authMapper;

    @RequestMapping("/user")
    public Object user(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            JSONObject jo = new JSONObject();
            AuthM authM = authMapper.selectById(userDetails.getUsername());
            Long qid = Long.valueOf(authM.getQid());
            Bot bot = Bot.getInstanceOrNull(qid);
            if (bot != null) {
                String nick = bot.getNick();
                if (Judge.isEmpty(nick)) nick = qid.toString();
                jo.put("nickname", nick);
                jo.put("t0", authM.getT0());
            } else {
                jo.put("nickname", "未在线");
                jo.put("t0", -1L);
            }
            jo.put("qid", authM.getQid());
            jo.put("icon", String.format(" https://q1.qlogo.cn/g?b=qq&nk=%s&s=640", qid));
            jo.put("expire", authM.getExp());
            return jo.toString();
        }
        return null;
    }
}
