package io.github.gdpl2112.dg_bot.service.optionals;

import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.PlainText;
import net.mamoe.mirai.message.data.SingleMessage;
import org.jetbrains.annotations.NotNull;

/**
 * @author github.kloping
 */
public abstract class BaseOptional implements ListenerHost {
    public abstract String getDesc();
    public abstract String getName();

    public abstract void run(MessageEvent event);

    @NotNull
    public static String getLineString(MessageEvent event) {
        StringBuilder line = new StringBuilder();
        for (SingleMessage singleMessage : event.getMessage()) {
            if (singleMessage instanceof PlainText) {
                line.append(((PlainText) singleMessage).getContent().trim());
            }
        }
        return line.toString().trim();
    }
}
