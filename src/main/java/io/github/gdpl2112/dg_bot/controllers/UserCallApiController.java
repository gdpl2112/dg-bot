package io.github.gdpl2112.dg_bot.controllers;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.built.callapi.CallApiService;
import io.github.gdpl2112.dg_bot.dao.CallTemplate;
import io.github.gdpl2112.dg_bot.mapper.CallTemplateMapper;
import io.github.kloping.judge.Judge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author github-kloping
 * @date 2023-07-31
 */
@RestController
@RequestMapping("/ca")
@PreAuthorize("hasAuthority('user')")
public class UserCallApiController {
    @Autowired
    CallTemplateMapper callTemplateMapper;
    @Autowired
    private CallApiService callApiService;

    @RequestMapping("/get_data")
    public Object getAll(@AuthenticationPrincipal UserDetails userDetails) {
        QueryWrapper<CallTemplate> qw = new QueryWrapper<>();
        qw.eq("qid", userDetails.getUsername());
        return callTemplateMapper.selectList(qw);
    }

    @RequestMapping("/delete")
    public Object delete(@AuthenticationPrincipal UserDetails userDetails,
                         @RequestParam("touch") String touch) {

        QueryWrapper<CallTemplate> qw = new QueryWrapper<>();
        qw.eq("qid", userDetails.getUsername());
        qw.eq("touch", touch);
        callTemplateMapper.delete(qw);
        callApiService.clear(userDetails.getUsername());
        return getAll(userDetails);
    }

    @RequestMapping("/append")
    public Object append(@AuthenticationPrincipal UserDetails userDetails,
                         @RequestParam("touch") String touch,
                         @RequestParam("out") String out,
                         @RequestParam("outArgs") String outArgs,
                         @RequestParam("jude") String jude,
                         @RequestParam("url") String url
    ) {
        if (Judge.isEmpty(touch)) return getAll(userDetails);
        if (Judge.isEmpty(url)) return getAll(userDetails);
        if (Judge.isEmpty(out)) return getAll(userDetails);
        boolean modify = true;
        CallTemplate template;
        QueryWrapper<CallTemplate> qw = new QueryWrapper<>();
        qw.eq("qid", userDetails.getUsername());
        qw.eq("touch", touch);
        template = callTemplateMapper.selectOne(qw);
        if (template == null) {
            modify = false;
            template = new CallTemplate();
        }
        template.setQid(userDetails.getUsername());
        template.setOut(out);
        template.setOutArgs(outArgs);
        template.setUrl(url);
        template.setTouch(touch);
        template.setJude(jude);
        if (modify) {
            callTemplateMapper.update(template, qw);
        } else {
            callTemplateMapper.insert(template);
        }
        callApiService.clear(userDetails.getUsername());
        return getAll(userDetails);
    }
}
