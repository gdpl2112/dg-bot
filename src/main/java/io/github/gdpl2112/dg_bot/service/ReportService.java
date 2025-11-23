package io.github.gdpl2112.dg_bot.service;

import io.github.kloping.date.DateUtils;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.message.data.ForwardMessageBuilder;
import net.mamoe.mirai.message.data.PlainText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * create on 21:02
 *
 * @author github kloping
 * @since 2025/11/23
 */
@Component
public class ReportService {

    private Map<String, List<String>> reportsMap = new HashMap<>();

    public int report(String bid, String msg) {
        List<String> reports = reportsMap.get(bid);
        if (reports == null) {
            reports = new java.util.ArrayList<>();
            reportsMap.put(bid, reports);
        }
        reports.add("[" + DateUtils.getFormat() + "] " + msg);
        return reports.size();
    }

    @Value("${report.bid:null}")
    String bid;
    @Value("${report.gid:null}")
    String gid;

    @Scheduled(cron = "30 10 0 * * ?")
    public void reportTo() {
        if (bid == null || gid == null || "null".equalsIgnoreCase(bid)) {
            reportsMap.clear();
            return;
        }
        try {
            Bot bot = Bot.getInstance(Long.parseLong(bid));
            if (bot != null) {
                Group group = bot.getGroup(Long.parseLong(gid));
                if (group != null) {
                    reportsMap.forEach((k, v) -> {
                        ForwardMessageBuilder builder = new ForwardMessageBuilder(group);
                        builder.add(bot.getId(), "报错信息上报", new PlainText("用户：" + k));
                        v.forEach(e -> {
                            builder.add(bot.getId(), "报错信息上报", new PlainText(e));
                        });
                        group.sendMessage(builder.build());
                    });
                }
                if (!reportsMap.isEmpty())
                    group.sendMessage("报错信息上报完成,详细日志请查看" + SF_0.format(new Date()) + "的日志.");
            }
        } finally {
            reportsMap.clear();
        }
    }


    private static final SimpleDateFormat SF_0 = new SimpleDateFormat("yyyy-MM-dd");
}
