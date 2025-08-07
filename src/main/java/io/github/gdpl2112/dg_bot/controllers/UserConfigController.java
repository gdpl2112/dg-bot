package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.service.ScriptService;
import io.github.gdpl2112.dg_bot.service.script.ScriptManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedList;
import java.util.List;

/**
 * @author github-kloping
 * @date 2023-07-19
 */
@RestController
@PreAuthorize("hasAuthority('user')")
@RequestMapping("/api")
public class UserConfigController {
    @Autowired
    ConfMapper confMapper;

    @RequestMapping("/config")
    public Conf getConfig(@AuthenticationPrincipal UserDetails userDetails) {
        Conf conf = confMapper.selectById(userDetails.getUsername());
        if (conf == null) {
            conf = new Conf();
            conf.setQid(userDetails.getUsername());
            confMapper.insert(conf);
        }
        return conf;
    }

    @RequestMapping("/conf-modify")
    public String confModify(@AuthenticationPrincipal UserDetails userDetails
            , @RequestParam(name = "key") String key
            , @RequestParam(name = "value") String value) {
        Conf conf = confMapper.selectById(userDetails.getUsername());
        if (conf == null) {
            conf = new Conf();
            conf.setQid(userDetails.getUsername());
        }
        if (key.equals("cd0")) {
            Integer cd0 = Integer.parseInt(value);
            conf.setCd0(cd0);
        } else {
            JSONObject jo = JSON.parseObject(JSON.toJSONString(conf));
            jo.put(key, value);
            conf = jo.toJavaObject(Conf.class);
        }
        return confMapper.updateById(conf) > 0 ? "成功" : "失败";
    }

    @PostMapping("/code-modify")
    public String codeModify(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String code) {
        Conf conf = confMapper.selectById(userDetails.getUsername());
        if (conf == null) {
            conf = new Conf();
            conf.setQid(userDetails.getUsername());
        }
        conf.setCode(code);
        Long bid = Long.valueOf(conf.getQid());
        ScriptManager.clearBidCache(bid);
        System.gc();
        return confMapper.updateById(conf) > 0 ? "成功" : "失败";
    }


    @Autowired
    ScriptService scriptService;

    @RequestMapping("/get-exception")
    public ScriptManager.ScriptException codeModify(@AuthenticationPrincipal UserDetails userDetails) {
        String id = userDetails.getUsername();
        if (ScriptManager.exceptionMap.containsKey(id)) {
            return ScriptManager.exceptionMap.get(id);
        } else return new ScriptManager.ScriptException("未发现报错", System.currentTimeMillis(), Long.valueOf(id));
    }

    @RequestMapping("/get-log")
    public List<String> getLogMsg(@AuthenticationPrincipal UserDetails userDetails) {
        String id = userDetails.getUsername();
        if (ScriptManager.PRINT_MAP.containsKey(id)) {
            return ScriptManager.PRINT_MAP.get(id);
        } else return new LinkedList<>();
    }
}
