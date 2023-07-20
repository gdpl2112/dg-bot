package io.github.gdpl2112.dg_bot.service;

import io.github.gdpl2112.dg_bot.mapper.PassiveMapper;
import io.github.kloping.MySpringTool.interfaces.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

/**
 * @author github-kloping
 * @date 2023-07-20
 */
@Service
public class PassiveService extends net.mamoe.mirai.event.SimpleListenerHost implements CommandLineRunner {
    @Autowired
    BotService service;
    @Autowired
    Logger logger;
    @Autowired
    PassiveMapper passiveMapper;

    @Override
    public void run(String... args) throws Exception {
        logger.info("正在加载passive 初始化任务");

        logger.info("加载passive 初始化任务完成");
    }

}
