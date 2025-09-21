package io.github.gdpl2112.dg_bot.controllers;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.Optional;
import io.github.gdpl2112.dg_bot.mapper.OptionalMapper;
import io.github.gdpl2112.dg_bot.service.listenerhosts.OptionalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author github.kloping
 */
@RestController
@PreAuthorize("hasAuthority('user')")
@RequestMapping("/api")
public class OptionalController {
    @Autowired
    OptionalMapper optionalMapper;

    @Autowired
    OptionalService optionalService;

    @RequestMapping("/opts")
    public Object getOpts(@AuthenticationPrincipal UserDetails userDetails) {
        String id = userDetails.getUsername();
        return optionalService.getOptionalDtos(id);
    }

    @RequestMapping("/opts/toggle")
    public Object tt(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String opt) {
        QueryWrapper<Optional> qw = new QueryWrapper<>();
        qw.eq("qid", userDetails.getUsername()).eq("opt", opt);
        Optional o = optionalMapper.selectOne(qw);
        if (o != null) {
            o.setOpen(!o.getOpen());
            optionalMapper.update(o, qw);
        } else {
            o = new Optional();
            o.setQid(userDetails.getUsername());
            o.setOpt(opt);
            o.setOpen(true);
            optionalMapper.insert(o);
        }
        return getOpts(userDetails);
    }
}
