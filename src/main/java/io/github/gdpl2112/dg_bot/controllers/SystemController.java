package io.github.gdpl2112.dg_bot.controllers;

import io.github.kloping.MySpringTool.annotations.Controller;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author github.kloping
 */
@Controller
public class SystemController {

    @RequestMapping("/favicon.ico")
    public String favicon(@AuthenticationPrincipal @Nullable UserDetails userDetails) {
        String redirectURL = "http://kloping.top/icon.jpg";
        if (userDetails != null)
            redirectURL = String.format("https://q1.qlogo.cn/g?b=qq&nk=%s&s=640", userDetails.getUsername());
        return "redirect:" + redirectURL;
    }
}
