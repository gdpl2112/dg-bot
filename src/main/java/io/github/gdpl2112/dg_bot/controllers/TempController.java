package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.dao.Passive;
import io.github.gdpl2112.dg_bot.mapper.PassiveMapper;
import io.github.kloping.file.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * @author github.kloping
 */
@RestController
public class TempController {
    @RequestMapping("/load")
    public void favicon(
            @RequestParam("qid") String qid
    ) throws IOException {
        String json = FileUtils.getStringFromFile(String.format("./temp-%s.json", qid));
        if (json == null) return;
        JSONObject jo = JSON.parseObject(json);
        for (String s : jo.keySet()) {
            JSONObject jo1 = jo.getJSONObject(s);
            if (jo1 == null) return;
            for (Object o : jo1.getJSONArray("vss")) {
                Passive passive = new Passive();
                passive.setQid(qid);
                passive.setTouch(s);
                passive.setOut(((JSONObject) o).getString("data"));
                passiveMapper.insert(passive);
            }
        }
    }

    @Autowired
    PassiveMapper passiveMapper;
}
