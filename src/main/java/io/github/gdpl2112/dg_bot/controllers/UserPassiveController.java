package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.Passive;
import io.github.gdpl2112.dg_bot.mapper.PassiveMapper;
import io.github.gdpl2112.dg_bot.pack.PassiveMessage;
import io.github.kloping.judge.Judge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author github-kloping
 * @since 2023-07-20
 */
@RestController
@PreAuthorize("hasAuthority('user')")
@RequestMapping("/api")
public class UserPassiveController {

    @Autowired
    PassiveMapper passiveMapper;

    @RequestMapping("/p-list")
    public Collection<PassiveMessage> passiveList(@AuthenticationPrincipal UserDetails userDetails) {
        QueryWrapper<Passive> qw = new QueryWrapper<>();
        qw.eq("qid", userDetails.getUsername());
        List<Passive> passiveList = passiveMapper.selectList(qw);
        Map<String, PassiveMessage> msgs = new HashMap<>();
        for (Passive passive : passiveList) {
            String touch = passive.getTouch();
            if (!msgs.containsKey(touch)) {
                PassiveMessage message = new PassiveMessage();
                message.setQid(passive.getQid());
                message.setTouch(passive.getTouch());
                message.getOuts().add(passive.getOut());
                msgs.put(touch, message);
            } else {
                msgs.get(touch).getOuts().add(passive.getOut());
            }
        }
        return msgs.values();
    }

    @RequestMapping("/p-add")
    public Collection<PassiveMessage> passiveAdd(@AuthenticationPrincipal UserDetails userDetails
            , @RequestBody String body) {
        JSONObject jo = JSON.parseObject(body);
        String touch = jo.getString("t0");
        String out = jo.getString("t1");
        Passive passive = new Passive();
        passive.setQid(userDetails.getUsername());
        passive.setTouch(touch);
        passive.setOut(out);
        passiveMapper.insert(passive);
        return passiveList(userDetails);
    }

    @RequestMapping("/p-del")
    public Boolean passiveDel(@AuthenticationPrincipal UserDetails userDetails,
                              @RequestBody String body) {
        try {
            JSONObject jo = JSON.parseObject(body);
            String touch = jo.getString("touch");
            String out = jo.getString("out");
            QueryWrapper<Passive> qw = new QueryWrapper<>();
            qw.eq("qid", userDetails.getUsername());
            int i = 0;
            if (Judge.isNotEmpty(touch)) {
                qw.eq("touch", touch);
                i++;
            }
            if (Judge.isNotEmpty(out)) {
                i++;
                qw.eq("out", out);
            }
            if (i > 0)
                return passiveMapper.delete(qw) > 0;
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
