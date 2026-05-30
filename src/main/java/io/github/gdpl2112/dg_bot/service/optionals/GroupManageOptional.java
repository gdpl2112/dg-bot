package io.github.gdpl2112.dg_bot.service.optionals;

import io.github.gdpl2112.dg_bot.service.listenerhosts.DefaultService;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.MemberPermission;
import net.mamoe.mirai.contact.NormalMember;
import net.mamoe.mirai.event.events.GroupAwareMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageSyncEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 群管模式扩展：为 BOT 管理员提供踢人、禁言、撤回指定消息的快捷指令。
 * 需要 BOT 在目标群拥有管理员或群主权限；操作者须是已配置的 BOT 管理员。
 * <p>
 * 指令格式：
 * <ul>
 *   <li>{@code 踢 @某人}                   —— 将 @成员 踢出群聊</li>
 *   <li>{@code 禁言 @某人 时长}             —— 禁言 @成员 指定时长（传 0 可解除禁言），最长 30 天</li>
 *   <li>引用某消息 + {@code 撤回}           —— 撤回被引用的那条消息</li>
 * </ul>
 * <p>
 * 时长单位支持：秒/s、分钟/min、小时/时/h、天/day/d、月/m，示例：{@code 60s}、{@code 5min}、{@code 2h}、{@code 1天}、{@code 1月}
 *
 * @author github.kloping
 */
@Component
public class GroupManageOptional implements BaseOptional {

    @Autowired
    private DefaultService defaultService;

    @Override
    public String getDesc() {
        return "群管模式[需BOT是群管/群主]: 管理员可踢人(踢 @xxx,拉黑踢 @xx)、禁言(禁言 @xxx 时长，支持秒/s/分钟/min/小时/时/h/天/day/d/月/m，最长30天)、撤回(引用消息+撤回)";
    }

    @Override
    public String getName() {
        return "群管模式";
    }

    @Override
    public void run(MessageEvent event) {
        // 仅处理群消息
        if (!(event instanceof GroupMessageEvent) && !(event instanceof GroupMessageSyncEvent)) return;

        GroupAwareMessageEvent groupEvent = (GroupAwareMessageEvent) event;
        Group group = groupEvent.getGroup();

        long bid = event.getBot().getId();
        long sid = event.getSender().getId();

        // 发送者须是 BOT 本身或已配置的管理员
        if (bid != sid && !defaultService.isAdmin(bid, sid)) return;

        // BOT 须在该群持有管理员或群主权限
        NormalMember botMember = group.getBotAsMember();
        if (botMember == null || botMember.getPermission() == MemberPermission.MEMBER) return;

        // 遍历消息链，提取命令关键词、@目标及引用消息
        String command = "";
        int muteSeconds = 0;
        At atTarget = null;
        QuoteReply quoteReply = null;

        for (SingleMessage msg : event.getMessage()) {
            if (msg instanceof PlainText) {
                String part = ((PlainText) msg).getContent().trim();
                if (command.isEmpty()) {
                    if (part.startsWith("踢")) {
                        command = "踢";
                    } else if (part.startsWith("拉黑踢")) {
                        command = "拉黑踢";
                    } else if (part.startsWith("禁言")) {
                        command = "禁言";
                        // 尝试从同段文本解析时长（如 "禁言60"、"禁言5min"）
                        String numPart = part.substring(2).trim();
                        if (!numPart.isEmpty()) {
                            int parsed = parseDuration(numPart);
                            if (parsed >= 0) muteSeconds = parsed;
                        }
                    } else if (part.equals("撤回")) {
                        command = "撤回";
                    }
                } else if ("禁言".equals(command) && muteSeconds == 0 && !part.isEmpty()) {
                    // 时长跟在 @mention 之后的独立文本段中（如 "禁言 @xxx 5min"）
                    int parsed = parseDuration(part);
                    if (parsed >= 0) muteSeconds = parsed;
                }
            } else if (msg instanceof At) {
                At at = (At) msg;
                // 排除 @BOT 自身
                if (at.getTarget() != bid) {
                    atTarget = at;
                }
            } else if (msg instanceof QuoteReply) {
                quoteReply = (QuoteReply) msg;
            }
        }

        switch (command) {
            case "踢":
                handleKick(group, atTarget, event, false);
                break;
            case "拉黑踢":
                handleKick(group, atTarget, event, true);
                break;
            case "禁言":
                handleMute(group, atTarget, muteSeconds, event);
                break;
            case "撤回":
                handleRecall(quoteReply, event);
                break;
        }
    }

    /**
     * 将时长字符串解析为秒数，支持单位：秒/s、分钟/min、小时/时/h、天/day/d、月/m。
     * 无单位时默认为秒；结果超过 30 天则截断至 30 天；传入 "0" 返回 0（解禁）。
     *
     * @param text 时长字符串，如 "60"、"5min"、"2h"、"1天"、"1月"
     * @return 对应秒数，解析失败返回 -1
     */
    private int parseDuration(String text) {
        if (text == null || text.isEmpty()) return -1;
        // 优先匹配较长单位（分钟/min/小时/day）以避免与单字符单位（m/h/d）产生歧义
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "^(\\d+)\\s*(分钟|min|小时|时|day|秒|[shmd天月])?$",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = p.matcher(text.trim());
        if (!matcher.matches()) return -1;
        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null || unit.isEmpty() || unit.equalsIgnoreCase("s") || unit.equals("秒")) {
            // 默认秒
        } else if (unit.equalsIgnoreCase("min") || unit.equals("分钟")) {
            value *= 60;
        } else if (unit.equalsIgnoreCase("h") || unit.equals("小时") || unit.equals("时")) {
            value *= 3600;
        } else if (unit.equalsIgnoreCase("d") || unit.equalsIgnoreCase("day") || unit.equals("天")) {
            value *= 86400L;
        } else if (unit.equalsIgnoreCase("m") || unit.equals("月")) {
            value *= 30L * 86400;
        } else {
            return -1;
        }
        // 最大禁言 30 天
        final long maxSeconds = 30L * 24 * 60 * 60;
        if (value > maxSeconds) value = maxSeconds;
        return (int) value;
    }

    /**
     * 将 @目标成员踢出群聊
     *
     * @param group    目标群
     * @param atTarget 消息中 @的目标成员
     * @param event    原始消息事件
     * @param b        是否拉黑
     */
    private void handleKick(Group group, At atTarget, MessageEvent event, boolean b) {
        if (atTarget == null) {
            event.getSubject().sendMessage("\"请 @要踢出的成员\"");
            return;
        }
        NormalMember target = group.get(atTarget.getTarget());
        if (target == null) {
            event.getSubject().sendMessage("\"未找到该成员\"");
            return;
        }
        try {
            target.kick("您已被移除群聊.", b);
            event.getSubject().sendMessage("\"已踢出 " + target.getNameCard()
                    + "(" + target.getId() + ")\"" + (b ? "并已拉黑" : "未拉黑"));
        } catch (Exception e) {
            event.getSubject().sendMessage("\"踢出失败: " + e.getMessage() + "\"");
        }
    }

    /**
     * 禁言 @目标成员
     *
     * @param group    目标群
     * @param atTarget 消息中 @的目标成员
     * @param seconds  禁言秒数，传 0 可解除禁言
     * @param event    原始消息事件
     */
    private void handleMute(Group group, At atTarget, int seconds, MessageEvent event) {
        if (atTarget == null) {
            event.getSubject().sendMessage("\"请 @要禁言的成员\"");
            return;
        }
        if (seconds < 0) {
            event.getSubject().sendMessage("\"禁言时间不能为负数\"");
            return;
        }
        NormalMember target = group.get(atTarget.getTarget());
        if (target == null) {
            event.getSubject().sendMessage("未找到该成员");
            return;
        }
        try {
            if (seconds <= 0) target.unmute();
            else target.mute(seconds);
            String result = seconds == 0
                    ? "\"已解除 " + target.getNameCard() + "(" + target.getId() + ") 的禁言\""
                    : "\"已禁言 " + target.getNameCard() + "(" + target.getId() + ") " + seconds + " 秒\"";
            event.getSubject().sendMessage(result);
        } catch (Exception e) {
            event.getSubject().sendMessage("\"禁言失败: " + e.getMessage() + "\"");
        }
    }

    /**
     * 撤回被引用的那条消息
     *
     * @param quoteReply 引用消息元素，从消息链中提取
     * @param event      原始消息事件
     */
    private void handleRecall(QuoteReply quoteReply, MessageEvent event) {
        if (quoteReply == null) {
            event.getSubject().sendMessage("\"请引用要撤回的消息\"");
            return;
        }
        try {
            MessageSource.recall(quoteReply.getSource());
            MessageSource.recall(event.getMessage());
        } catch (Exception e) {
            event.getSubject().sendMessage("\"撤回失败: " + e.getMessage() + "\"");
        }
    }
}
