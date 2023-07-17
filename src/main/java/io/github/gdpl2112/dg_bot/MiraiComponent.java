package io.github.gdpl2112.dg_bot;

import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @author github-kloping
 * @date 2023-07-17
 */
@Component
public class MiraiComponent implements CommandLineRunner {
    @Override
    public void run(String... args) throws Exception {
        MiraiConsoleTerminalLoader.main(args);
    }
}
