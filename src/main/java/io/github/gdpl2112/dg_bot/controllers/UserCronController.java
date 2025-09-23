package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.CronMessage;
import io.github.gdpl2112.dg_bot.mapper.CronMapper;
import io.github.gdpl2112.dg_bot.service.CronService;
import io.github.kloping.judge.Judge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

/**
 * @author github-kloping
 * @since 2023-07-19
 */
@RestController
@PreAuthorize("hasAuthority('user')")
@RequestMapping("/api")
public class UserCronController {

    @Autowired
    CronMapper cronMapper;

    @RequestMapping("/cronAdd")
    public String cronAdd(@AuthenticationPrincipal UserDetails userDetails,
                          @RequestBody String body, HttpServletRequest request) {
        try {
            body = URLDecoder.decode(body, "UTF-8");
            while (body.endsWith("=")) {
                body = body.substring(0, body.length() - 1);
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String desc = "";
        StringBuilder cronBuilder = new StringBuilder();
        //0秒
        cronBuilder.append("8 ");

        JSONObject jo = JSON.parseObject(body);
        String content = jo.getString("content");
        if (Judge.isEmpty(content)) return "内容为空";
        String targetId = jo.getString("targetId");
        if (Judge.isEmpty(targetId)) return "目标id为空";
        String targetType = jo.getString("tid-type");
        String hour = jo.getString("hour");
        String mil = jo.getString("mil");
        cronBuilder.append(mil).append(" ").append(hour).append(" ");
        desc = String.format("%s点%s分", hour, mil);
        String l1type = jo.getString("l1-type");
        if ("week".equals(l1type)) {
            String wks = jo.getString("week");
            if (Judge.isEmpty(wks)) return "未选择星期";
            String[] weeks = wks.split("");
            cronBuilder.append("? * ");
            for (String week : weeks) {
                cronBuilder.append(week).append(",");
                Integer wint = Integer.valueOf(week);
                int n = ((wint + 5) % 7);
                n++;
                desc = n + "," + desc;
            }
            desc = "每星期" + desc;
            cronBuilder.delete(cronBuilder.length() - 1, cronBuilder.length());
            cronBuilder.append(" ");
        } else {
            String day_type = jo.getString("for-day");
            if ("eve".equals(day_type)) {
                cronBuilder.append("* ");
                desc = "每日" + desc;
            } else {
                String day = jo.getString("day");
                cronBuilder.append(day).append(" ");
                desc = day + "日" + desc;
            }
            String month_type = jo.getString("for-month");
            if ("eve".equals(month_type)) {
                cronBuilder.append("* ");
                desc = "每月" + desc;
            } else {
                String month = jo.getString("month");
                cronBuilder.append(month).append(" ");
                desc = month + "月" + desc;
            }
            cronBuilder.append("?");
        }
        System.out.println(desc);
        String cron = cronBuilder.toString();
        System.out.println(cron);
        CronMessage cronMessage = new CronMessage();
        cronMessage.setQid(userDetails.getUsername());
        cronMessage.setCron(cron);
        String tid = targetType + targetId;
        cronMessage.setTargetId(tid);
        cronMessage.setMsg(content);
        cronMessage.setDesc(desc);
        int state = -1;
        try {
            return (state = cronMapper.insert(cronMessage)) > 0 ? "ok" : "error";
        } finally {
            if (state > 0) {
                QueryWrapper qw = new QueryWrapper();
                qw.eq("qid", userDetails.getUsername());
                qw.eq("cron", cron);
                qw.eq("desc", desc);
                qw.eq("target_id", tid);
                qw.eq("msg", content);
                Integer id = cronMapper.selectOne(qw).getId();
                cronMessage.setId(id);
                cronService.appendTask(cronMessage);
            }
        }
    }

    @Autowired
    CronService cronService;

    @RequestMapping("/cron-list")
    public List cronList(@AuthenticationPrincipal UserDetails userDetails) {
        QueryWrapper<CronMessage> qw = new QueryWrapper<>();
        qw.eq("qid", userDetails.getUsername());
        List<CronMessage> cronMessages = cronMapper.selectList(qw);
        List list = new ArrayList();
        for (CronMessage e : cronMessages) {
            String aid = e.getTargetId().substring(1);
            JSONObject jo = JSON.parseObject(JSON.toJSONString(e));
            jo.put("icon", e.getTargetId().substring(0, 1).equals("g") ?
                    String.format("http://p.qlogo.cn/gh/%s/%s/640", aid,aid) :
                    String.format("https://q1.qlogo.cn/g?b=qq&nk=%s&s=640", aid));
            list.add(jo);
        }
        return list;
    }

    @RequestMapping("/cron-del")
    public List cronDel(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String id) {
        cronService.del(id);
        return cronList(userDetails);
    }

}
