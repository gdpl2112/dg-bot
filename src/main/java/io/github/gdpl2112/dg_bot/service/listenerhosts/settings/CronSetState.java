package io.github.gdpl2112.dg_bot.service.listenerhosts.settings;

import io.github.gdpl2112.dg_bot.dao.CronMessage;
import io.github.gdpl2112.dg_bot.service.CronService;
import io.github.gdpl2112.dg_bot.utils.CronParserUtil;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.User;

import java.util.List;

/**
 *
 * create on 12:13
 *
 * @author github kloping
 * @since 2025/9/22
 */
public class CronSetState implements BotState {

    private CronService service;
    private Bot bot;
    private List<CronMessage> list;

    public CronSetState(CronService service, Bot bot) {
        this.service = service;
        this.bot = bot;

        list = service.getCronMessages(bot.getId());
    }

    @Override
    public String getName() {
        return "定时设置";
    }

    @Override
    public String getWelcomeMessage() {
        StringBuilder sb = new StringBuilder("输入指定序号删除\n输入ID开始添加定时任务");
        sb.append("\n 0. 退出");
        for (int i = 0; i < list.size(); i++) {
            CronMessage cronMessage = list.get(i);
            sb.append("\n ").append(i + 1).append(" . 在'").append(cronMessage.getDesc())
                    .append("'给'").append(cronMessage.getTargetId()).append("'发送:")
                    .append(cronMessage.getMsg());
        }
        return sb.toString();
    }

    private String nid;
    private String cron;
    private String desc;

    @Override
    public String handleInput(User user, String input, Context context) {
        if (cron != null) {
            if (CronParserUtil.isValidCronExpression(cron)) {
                CronMessage cronMessage = new CronMessage();
                cronMessage.setQid(String.valueOf(bot.getId()));
                cronMessage.setCron(cron);
                cronMessage.setDesc(desc);
                cronMessage.setTargetId(nid);
                cronMessage.setMsg(input);
                service.mapper.insert(cronMessage);
                service.appendTask(cronMessage);
                list.add(cronMessage);
                nid = null;
                desc = null;
                cron = null;
                return "添加成功\n" + getWelcomeMessage();
            } else {
                return "无效的定时任务表达式!";
            }
        }
        if (nid != null) {
            try {
                cron = CronParserUtil.convertDescriptionToCron(input);
                desc = input;
                return "设置成功\n请输入要发送的消息内容";
            } catch (UnsupportedOperationException e) {
                return "无效的定时任务表达式!";
            }
        }
        try {
            Integer v = Integer.parseInt(input);
            if (v > 0 && v <= list.size()) {
                v = v - 1;
                CronMessage cronMessage = list.get(v);
                service.del(String.valueOf(cronMessage.getId()));
                list.remove(cronMessage);
                return "删除成功\n" + getWelcomeMessage();
            }
        } catch (NumberFormatException e) {

        }
        try {
            Long tid = Long.parseLong(input);
            Contact contact = null;
            String p = "g";
            contact = context.getBot().getGroup(tid);
            if (contact == null) {
                contact = context.getBot().getFriend(tid);
                p = "f";
            }
            if (contact == null) return "不存在该ID的群聊或好友!";
            nid = p + tid;
            return "请输入中文的定时任务表达式 例如:"
                    + "\n - 每天12点30分"
                    + "\n - 每月15号13点45分"
                    + "\n - 9月22日20点8分";
        } catch (NumberFormatException e) {

        }
        return "无效输入!";
    }
}
