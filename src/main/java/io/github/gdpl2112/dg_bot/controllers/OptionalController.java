package io.github.gdpl2112.dg_bot.controllers;

import io.github.gdpl2112.dg_bot.service.listenerhosts.OptionalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author github.kloping
 */
@RestController
@PreAuthorize("hasAuthority('user')")
@RequestMapping("/api")
public class OptionalController {
    @Autowired
    OptionalService optionalService;

    /**
     * 获取所有可选功能及其启用的群列表
     */
    @RequestMapping("/opts")
    public Object getOpts(@AuthenticationPrincipal UserDetails userDetails) {
        String id = userDetails.getUsername();
        return optionalService.getOptionalDtos(id);
    }

    /**
     * 为某功能启用/禁用某个群
     * @param opt 功能ID（bean名）
     * @param tid 目标ID，格式: "g" + 群号 或 "f" + 好友号
     * @param enabled true=启用, false=禁用
     */
    @RequestMapping("/opts/set-group")
    public Object setGroup(@AuthenticationPrincipal UserDetails userDetails,
                           @RequestParam String opt,
                           @RequestParam String tid,
                           @RequestParam boolean enabled) {
        String id = userDetails.getUsername();
        optionalService.setGroupEnabled(id, opt, tid, enabled);
        return optionalService.getOptionalDtos(id);
    }

    /**
     * 获取某功能已启用的群 tid 列表
     */
    @RequestMapping("/opts/groups")
    public List<String> getGroups(@AuthenticationPrincipal UserDetails userDetails,
                                  @RequestParam String opt) {
        String id = userDetails.getUsername();
        return optionalService.getEnabledGroups(id, opt);
    }

    /**
     * 为某功能全开/全关所有群
     * @param opt 功能ID（bean名）
     * @param enabled true=全开, false=全关
     * @param tids 目标ID列表，逗号分隔
     */
    @RequestMapping("/opts/set-all")
    public Object setAll(@AuthenticationPrincipal UserDetails userDetails,
                         @RequestParam String opt,
                         @RequestParam boolean enabled,
                         @RequestParam String tids) {
        String id = userDetails.getUsername();
        String[] tidArr = tids.split(",");
        for (String tid : tidArr) {
            if (!tid.trim().isEmpty()) {
                optionalService.setGroupEnabled(id, opt, tid.trim(), enabled);
            }
        }
        return optionalService.getOptionalDtos(id);
    }
}
