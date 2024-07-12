package io.github.gdpl2112.dg_bot.service.optionals;

import net.mamoe.mirai.contact.MemberPermission;
import net.mamoe.mirai.contact.NormalMember;
import net.mamoe.mirai.event.events.GroupAwareMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageSyncEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import org.springframework.stereotype.Component;

/**
 * @author github.kloping
 */
@Component
public class SpecialTitle extends BaseOptional {

    @Override
    public String getDesc() {
        return "群内头衔设置[需要BOT是群主]所有群员通过[我要头衔xxx]获得头衔";
    }

    @Override
    public String getName() {
        return "自动头衔";
    }

    @Override
    public void run(MessageEvent event) {
        GroupAwareMessageEvent game = null;
        if (event instanceof GroupMessageEvent || event instanceof GroupMessageSyncEvent) {
            game = (GroupAwareMessageEvent) event;
        }
        if (game == null) return;
        NormalMember member = game.getGroup().getBotAsMember();
        if (member.getPermission() == MemberPermission.OWNER) {
            String out = getLineString(event);
            if (out.startsWith("我要头衔")) {
                String title = out.substring(4);
                NormalMember m1 = game.getGroup().get(game.getSender().getId());
                m1.setSpecialTitle(title);
            }
        }
    }
}
