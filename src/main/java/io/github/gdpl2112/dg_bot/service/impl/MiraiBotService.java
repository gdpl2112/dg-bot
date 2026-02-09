package io.github.gdpl2112.dg_bot.service.impl;

import io.github.gdpl2112.dg_bot.service.BotService;
import io.github.gdpl2112.dg_bot.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.message.code.MiraiCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author github-kloping
 * @since 2023-07-17
 */
@Service
@Slf4j
public class MiraiBotService implements BotService {

    @Autowired
    ReportService reportService;

    @Override
    public void send(String qid, String targetId, String msg) {
        Long bid = Long.valueOf(qid);
        Bot bot = Bot.getInstanceOrNull(bid);
        if (bot == null) {
            log.warn("{} 用户实例获取失败! 可能掉线或未登录", qid);
            reportService.report(String.valueOf(bid), "cron任务执行失败! 用户实例获取失败! 可能掉线或未登录");
        } else {
            Contact contact = getContact(bot, targetId);
            if (contact == null) {
                log.warn("{} 用户实例 cron 任务发送目标{}获取失败!", qid, targetId);
                reportService.report(String.valueOf(bid), String.format("%s 用户实例 cron 任务发送目标%s获取失败!", qid, targetId));
            } else {
                contact.sendMessage(MiraiCode.deserializeMiraiCode(msg));
            }
        }
    }

    public Contact getContact(Bot bot, String targetId) {
        Contact contact;
        try {
            Long id = Long.valueOf(targetId);
            contact = bot.getFriend(id);
        } catch (NumberFormatException e) {
            String type = targetId.substring(0, 1);
            Long id = Long.valueOf(targetId.substring(1));
            if (type.equals("g")) {
                contact = bot.getGroup(id);
            } else {
                contact = bot.getFriend(id);
            }
        }
        return contact;
    }
}
