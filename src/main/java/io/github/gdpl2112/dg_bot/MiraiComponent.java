package io.github.gdpl2112.dg_bot;

import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.gdpl2112.dg_bot.service.CronService;
import io.github.kloping.MySpringTool.interfaces.Logger;
import net.mamoe.mirai.console.terminal.MiraiConsoleImplementationTerminal;
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.BotOnlineEvent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author github-kloping
 * @date 2023-07-17
 */
@Component
public class MiraiComponent extends SimpleListenerHost implements CommandLineRunner {
    final CronService service0;
    final ThreadPoolTaskExecutor executor;
    final AuthMapper authMapper;
    final Logger logger;

    public MiraiComponent(CronService service0, ThreadPoolTaskExecutor executor, AuthMapper authMapper, Logger logger) {
        this.service0 = service0;
        this.executor = executor;
        this.authMapper = authMapper;
        this.logger = logger;
    }

    @Override
    public void run(String... args) throws Exception {
        executor.submit(() -> {
            MiraiConsoleTerminalLoader.INSTANCE.startAsDaemon(new MiraiConsoleImplementationTerminal());
        });
        GlobalEventChannel.INSTANCE.registerListenerHost(service0);
        GlobalEventChannel.INSTANCE.registerListenerHost(this);
    }

    @EventHandler
    public void onBotOnline(BotOnlineEvent event) {
        Long bid = event.getBot().getId();
        AuthM auth = authMapper.selectById(bid);
        if (auth == null) {
            logger.info(String.format("%s登录成功,正在生成管理秘钥", bid));
            auth = new AuthM();
            auth.setQid(bid.toString());
            auth.setAuth(UUID.randomUUID().toString());
            authMapper.insert(auth);
            logger.info(String.format("%s管理秘钥生成完成:%s", bid, auth.getAuth()));
        } else {
            logger.info(String.format("%s登录成功,管理秘钥:%s", bid, auth.getAuth()));
        }
    }
}
