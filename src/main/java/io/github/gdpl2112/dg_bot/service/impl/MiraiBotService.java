package io.github.gdpl2112.dg_bot.service.impl;

import io.github.gdpl2112.dg_bot.service.BotService;
import io.github.kloping.MySpringTool.interfaces.Logger;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.message.code.MiraiCode;
import org.springframework.stereotype.Service;

/**
 * @author github-kloping
 * @date 2023-07-17
 */
@Service
public class MiraiBotService implements BotService {

    final Logger logger;

    public MiraiBotService(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void send(String qid, String targetId, String msg) {
        Long bid = Long.valueOf(qid);
        Bot bot = Bot.getInstanceOrNull(bid);
        if (bot == null) {
            logger.waring(String.format("%s 用户实例获取失败! 可能掉线或未登录", qid));
        } else {
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
            if (contact == null) {
                logger.waring(String.format("%s 用户实例 cron 任务发送目标%s获取失败!", qid, targetId));
            } else {
                contact.sendMessage(MiraiCode.deserializeMiraiCode(msg));
            }
        }
    }
}
