package io.github.gdpl2112.dg_bot.controllers;

import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * @author github.kloping
 */
@RestController
@RequestMapping("/m")
@PreAuthorize("hasAuthority('admin')")
public class ManagerController {
    @Autowired
    AuthMapper authMapper;

    @RequestMapping("list")
    public List<AuthM> list() {
        return authMapper.selectList(null);
    }

    @RequestMapping("modify")
    public List<AuthM> modify(@RequestParam("qid") Long qid, @RequestParam("exp") Long exp, @RequestParam("auth") String auth) {
        AuthM authM = authMapper.selectById(qid);
        authM.setExp(exp);
        authM.setAuth(auth);
        int i = authMapper.updateById(authM);
        return list();
    }

    private static final SimpleDateFormat SF_0 = new SimpleDateFormat("yyyy-MM-dd:HH:mm:ss");

    @RequestMapping("get-exp")
    public Long getExp(@RequestParam("y") Integer y, @RequestParam("m") Integer m, @RequestParam("d") Integer d) {
        try {
            return SF_0.parse(String.format("%s-%s-%s:12:01:00", y, m, d)).getTime();
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }
}
