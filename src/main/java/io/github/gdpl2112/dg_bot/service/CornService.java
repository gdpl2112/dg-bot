package io.github.gdpl2112.dg_bot.service;

import io.github.gdpl2112.dg_bot.mapper.CornMapper;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author github.kloping
 */
@Service
public class CornService extends net.mamoe.mirai.event.SimpleListenerHost {
    @Autowired
    CornMapper mapper;

    @Override
    public void handleException(@NotNull CoroutineContext context, @NotNull Throwable exception) {

    }

}
