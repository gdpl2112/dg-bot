package io.github.gdpl2112.dg_bot.controllers;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.Passive;
import io.github.gdpl2112.dg_bot.mapper.PassiveMapper;
import io.github.gdpl2112.dg_bot.pack.PassiveMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author github-kloping
 * @date 2023-07-20
 */
@RestController
@PreAuthorize("hasAuthority('user')")
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
    public Collection<PassiveMessage> passiveAdd(@AuthenticationPrincipal UserDetails userDetails,
                                                 @RequestParam(name = "t0") String touch,
                                                 @RequestParam(name = "t1") String out) {
        Passive passive = new Passive();
        passive.setQid(userDetails.getUsername());
        passive.setTouch(touch);
        passive.setOut(out);
        passiveMapper.insert(passive);
        return passiveList(userDetails);
    }

    @RequestMapping("/p-del")
    public Boolean passiveDel(@AuthenticationPrincipal UserDetails userDetails,
                              @RequestParam String touch, @RequestParam @Nullable String out) {
        QueryWrapper<Passive> qw = new QueryWrapper<>();
        qw.eq("qid", userDetails.getUsername());
        qw.eq("touch", touch);
        if (out != null && !"undefined".equals(out)) {
            qw.eq("out", out);
        }
        return passiveMapper.delete(qw) > 0;
    }
}
