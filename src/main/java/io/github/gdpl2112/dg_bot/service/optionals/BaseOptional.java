package io.github.gdpl2112.dg_bot.service.optionals;

import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.MessageEvent;

/**
 * @author github.kloping
 */
public interface BaseOptional extends ListenerHost {
    String getDesc();

    String getName();

    void run(MessageEvent event);
}
