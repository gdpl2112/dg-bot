package io.github.gdpl2112.dg_bot.controllers;

import io.github.gdpl2112.dg_bot.dao.V11Conf;
import io.github.gdpl2112.dg_bot.service.V11AutoService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author github kloping
 * @date 2025/7/18-16:07
 */
@RestController
@RequestMapping("/api/v11")
@PreAuthorize("hasAuthority('user')")
public class UserV11Controller {
    @Autowired
    @Lazy
    V11AutoService v11;

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
            default:
                return null;
        }
        v11. mapper.updateById(v11Conf);
        return v11.getV11Conf(qid);
    }

    @RequestMapping("/autoLikeNow")
    public String autoLikeNow(@AuthenticationPrincipal UserDetails userDetails) {
        String qid = userDetails.getUsername();
        return "已执行\n执行结果:" + v11.likeNow(qid);
    }

    @RequestMapping("/autoLikeYesterdayNow")
    public String autoLikeYesterdayNow(@AuthenticationPrincipal UserDetails userDetails) {
        String qid = userDetails.getUsername();
        return "已执行\n执行结果:" + v11.yesterdayLieNow(qid);
    }
}
