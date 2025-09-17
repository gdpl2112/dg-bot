package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.dto.BotInfo;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.kloping.url.UrlUtils;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author github kloping
 * @date 2025/6/2-22:06
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class PreController {

    @Autowired
    AuthMapper authMapper;

    private Map<String, String> t2url = new HashMap<>();

    @GetMapping("/bot/avatar")
    public void avatar0(@RequestParam(name = "t") String t
            , HttpServletRequest request
            , HttpServletResponse response
    ) {
        synchronized (t2url) {
            try {
                String url = t2url.get(t);
                byte[] bytes = UrlUtils.getBytesFromHttpUrl(url);
                response.setContentType("image/png");
                response.getOutputStream().write(bytes);
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
    }

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

    @GetMapping("/bot/alist")
    public JSONArray alists(HttpServletRequest request) {
        List<AuthM> auths = authMapper.selectList(null);
        JSONArray array = new JSONArray();
        for (AuthM auth : auths) {
            JSONObject jo = new JSONObject();
            String aid = null;
            jo.put("id", (aid = filterAid(auth.getQid())));
            Bot bot = Bot.getInstanceOrNull(Long.valueOf(auth.getQid()));
            jo.put("nick", bot != null ? bot.getNick() : "离线");
            String avatar = "https://q1.qlogo.cn/g?b=qq&nk=" + auth.getQid() + "&s=640";
            jo.put("avatar", filterAa(avatar, aid, request));
            jo.put("expire", auth.getExp());
            jo.put("online", bot != null && bot.isOnline());
            array.add(jo);
        }
        return array;
    }

    private String filterAa(String avatar, String aid, HttpServletRequest request) {
        synchronized (t2url) {
            t2url.put(aid, avatar);
        }
        String uri0 = request.getRequestURL().toString();
        return uri0.replace("/alist", "/avatar?t=" + aid);
    }

    private String filterAid(String qid) {
        String st = qid.substring(0, 2);
        String et = qid.substring(qid.length() - 2);
        return st + "******" + et;
    }
}
