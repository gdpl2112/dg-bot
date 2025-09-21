package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author github kloping
 * @since 2025/8/25-09:58
 */
@RequestMapping("/api")
@RestController
@PreAuthorize("hasAuthority('user')")
public class AuthController {

    @Autowired
    AuthMapper authMapper;

    @RequestMapping("/mpwd")
    public String pwd(@AuthenticationPrincipal UserDetails userDetails, @RequestBody String pwd) {
        JSONObject jo = JSON.parseObject(pwd);
        pwd = jo.getString("pwd");
        UpdateWrapper<AuthM> uw = new UpdateWrapper<>();
        uw.eq("qid", userDetails.getUsername());
        uw.set("auth", pwd);
        return authMapper.update(null, uw) > 0 ? "ok" : "error";
    }

    @RequestMapping("/cpwd")
    public String pwd(@AuthenticationPrincipal UserDetails userDetails) {
        AuthM authM = authMapper.selectOne(new UpdateWrapper<AuthM>().eq("qid", userDetails.getUsername()));
        return authM.getAuth();
    }
}
