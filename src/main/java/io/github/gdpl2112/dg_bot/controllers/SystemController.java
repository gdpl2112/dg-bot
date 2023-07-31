package io.github.gdpl2112.dg_bot.controllers;

import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author github.kloping
 */
@RestController
public class SystemController {

    @RequestMapping("/favicon.ico")
    public void favicon(HttpServletResponse resp, @AuthenticationPrincipal @Nullable UserDetails userDetails) throws IOException {
        String redirectURL = "http://kloping.top/icon.jpg";
        if (userDetails != null)
            redirectURL = String.format("https://q1.qlogo.cn/g?b=qq&nk=%s&s=640", userDetails.getUsername());
        resp.sendRedirect(redirectURL);
    }

    @RequestMapping("/icon")
    public void icon(HttpServletResponse resp, @AuthenticationPrincipal @Nullable UserDetails userDetails) throws IOException {
        String redirectURL = "http://kloping.top/icon.jpg";
        if (userDetails != null)
            redirectURL = String.format("https://q1.qlogo.cn/g?b=qq&nk=%s&s=640", userDetails.getUsername());
        resp.sendRedirect(redirectURL);
    }
}
