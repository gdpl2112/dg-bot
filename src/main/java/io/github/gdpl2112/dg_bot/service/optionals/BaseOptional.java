package io.github.gdpl2112.dg_bot.service.optionals;

import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;

/**
 * @author github.kloping
 */
public abstract class BaseOptional implements ListenerHost {
    public abstract String getDesc();
    public abstract String getName();

    public abstract void run(GroupMessageEvent event);

    public abstract void run(FriendMessageEvent event);
}
