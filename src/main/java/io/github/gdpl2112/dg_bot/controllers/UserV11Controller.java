package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.dao.V11Conf;
import io.github.gdpl2112.dg_bot.service.v11s.V11AutoLikeService;
import net.mamoe.mirai.Bot;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * @author github kloping
 * @since 2025/7/18-16:07
 */
@RestController
@RequestMapping("/api/v11")
@PreAuthorize("hasAuthority('user')")
public class UserV11Controller {
    @Autowired
    @Lazy
    V11AutoLikeService v11;

    @RequestMapping("/get-conf")
    public V11Conf getConf(@AuthenticationPrincipal UserDetails userDetails) {
        String id = userDetails.getUsername();
        return v11.getV11Conf(id);
    }

    @RequestMapping("/modify-conf")
    public @NotNull V11Conf modifyConf(@AuthenticationPrincipal UserDetails userDetails,
                                       @RequestParam(name = "key") String key,
                                       @RequestParam(name = "value") String value) {
        String qid = userDetails.getUsername();
        V11Conf v11Conf = v11.getV11Conf(qid);
        switch (key) {
            case "autoLike":
                v11Conf.setAutoLike(Boolean.parseBoolean(value));
                break;
            case "needMaxLike":
                v11Conf.setNeedMaxLike(Boolean.parseBoolean(value));
                break;
            case "autoZoneLike":
                v11Conf.setAutoZoneLike(Boolean.parseBoolean(value));
                break;
            case "autoLikeYesterday":
                v11Conf.setAutoLikeYesterday(Boolean.parseBoolean(value));
                break;
            case "signGroups":
                v11Conf.setSignGroups(value);
                break;
            case "zoneComment":
                v11Conf.setZoneComment(value);
                break;
            case "zoneWalks":
                v11Conf.setZoneWalks(value);
                break;
            case "likeBlack":
                v11Conf.setLikeBlack(value);
                break;
            case "likeWhite":
                v11Conf.setLikeWhite(value);
                break;
            case "zoneEvl":
                v11Conf.setZoneEvl(Integer.valueOf(value));
                break;
            default:
                return null;
        }
        v11.updateById(v11Conf);
        return v11.getV11Conf(qid);
    }

    @RequestMapping("/autoLikeNow")
    public String autoLikeNow(@AuthenticationPrincipal UserDetails userDetails) {
        String qid = userDetails.getUsername();
        return "已执行\n执行结果:" + v11.likeNow(qid);
    }

    @RequestMapping("/signGroupNow")
    public String signGroupNow(@AuthenticationPrincipal UserDetails userDetails) {
        String qid1 = userDetails.getUsername();
        Long qid = Long.parseLong(qid1);
        v11.signNow(Bot.getInstance(qid));
        return "已执行";
    }

    @RequestMapping("/autoLikeYesterdayNow")
    public String autoLikeYesterdayNow(@AuthenticationPrincipal UserDetails userDetails) {
        String qid = userDetails.getUsername();
        return "已执行\n执行结果:" + v11.yesterdayLieNow(qid);
    }

    //获得所有群聊(ID,图标和名字)
    @RequestMapping("/getGroups")
    public Object getGroups(@AuthenticationPrincipal UserDetails userDetails) {
        Long qid = Long.parseLong(userDetails.getUsername());
        Bot bot = Bot.getInstanceOrNull(qid);
        if (bot != null && bot.isOnline()) {
            List<JSONObject> list = new ArrayList<>();
            bot.getGroups().forEach(group -> {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", group.getId());
                jsonObject.put("name", group.getName());
                jsonObject.put("icon", group.getAvatarUrl());
                list.add(jsonObject);
            });
            return list;
        } else return ResponseEntity.badRequest().body("未在线");
    }
}
