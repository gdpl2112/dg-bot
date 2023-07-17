package io.github.gdpl2112.dg_bot;

import io.github.gdpl2112.dg_bot.service.CornService;
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader;
import net.mamoe.mirai.event.GlobalEventChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @author github-kloping
 * @date 2023-07-17
 */
@Component
public class MiraiComponent implements CommandLineRunner {
    @Autowired
    CornService service0;

    @Override
    public void run(String... args) throws Exception {
        GlobalEventChannel.INSTANCE.registerListenerHost(service0);
        MiraiConsoleTerminalLoader.main(args);
    }
}
