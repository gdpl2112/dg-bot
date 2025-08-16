package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.gdpl2112.dg_bot.service.V11AutoService;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author github.kloping
 */
@RestController
@RequestMapping("/api/m")
@PreAuthorize("hasAuthority('admin')")
public class ManagerController {

    @Autowired
    AuthMapper authMapper;

    private static final SimpleDateFormat SF_DD = new SimpleDateFormat("dd");
    private static final SimpleDateFormat SF_MM = new SimpleDateFormat("MM");
    private static final SimpleDateFormat SF_YY = new SimpleDateFormat("yyyy");

    @RequestMapping("list")
    public List<AuthM> list() {
        List list = new ArrayList();
        for (AuthM authM : authMapper.selectList(null)) {
            JSONObject jo = new JSONObject();
            Long qid = Long.valueOf(authM.getQid());
            jo.put("qid", qid);
            jo.put("auth", authM.getAuth());
            jo.put("exp", authM.getExp());
            jo.put("y", SF_YY.format(authM.getExp()));
            jo.put("m", SF_MM.format(authM.getExp()));
            jo.put("d", SF_DD.format(authM.getExp()));
            Bot bot = Bot.getInstanceOrNull(qid);
            if (bot != null && bot.isOnline()) {
                String nick = bot.getNick();
                jo.put("t0", authM.getT0());
            } else {
                jo.put("t0", -1L);
            }
            list.add(jo);
        }
        return list;
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

    @RequestMapping("exp-ymd")
    public String[] expYmd(@RequestParam("exp") Long exp) {
        return new String[]{
                SF_YY.format(exp),
                SF_MM.format(exp),
                SF_DD.format(exp)};
    }

    @Autowired
    @Lazy
    V11AutoService v11AutoService;

    @RequestMapping("/autoLike")
    public String autoLike() {
        v11AutoService.autoLike();
        return "OK";
    }
}
