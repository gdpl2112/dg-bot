package io.github.gdpl2112.dg_bot.service.optionals;

import net.mamoe.mirai.event.ListenerHost;

/**
 * @author github.kloping
 */
public abstract class BaseOptional implements ListenerHost {
    public abstract String getDesc();
    public abstract String getName();
}
