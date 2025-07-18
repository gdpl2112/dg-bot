package io.github.gdpl2112.dg_bot.controllers;

import io.github.gdpl2112.dg_bot.dao.V11Conf;
import io.github.gdpl2112.dg_bot.mapper.V11ConfMapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
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
    V11ConfMapper mapper;

    @RequestMapping("/get-conf")
    public V11Conf getConf(@AuthenticationPrincipal UserDetails userDetails) {
        String id = userDetails.getUsername();
        return getV11Conf(id);
    }

    public @NotNull V11Conf getV11Conf(String id) {
        V11Conf v11Conf = mapper.selectById(id);
        if (v11Conf == null) {
            v11Conf = new V11Conf();
            v11Conf.setQid(id);
            v11Conf.setAutoLike(true);
            v11Conf.setAutoZoneLike(true);
            v11Conf.setAutoLikeYesterday(true);
            v11Conf.setSignGroups("");
            v11Conf.setZoneComment("");
            mapper.insert(v11Conf);
        }
        return v11Conf;
    }

    @RequestMapping("/modify-conf")
    public @NotNull V11Conf modifyConf(@AuthenticationPrincipal UserDetails userDetails,
                                       @RequestParam(name = "key") String key,
                                       @RequestParam(name = "value") String value) {
        String qid = userDetails.getUsername();
        V11Conf v11Conf = getV11Conf(qid);
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
        mapper.updateById(v11Conf);
        return getV11Conf(qid);
    }
}
