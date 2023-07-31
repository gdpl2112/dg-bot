package io.github.gdpl2112.dg_bot;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.built.BuiltPlugin;
import io.github.gdpl2112.dg_bot.built.callapi.CallApiService;
import io.github.gdpl2112.dg_bot.built.ScriptService;
import io.github.gdpl2112.dg_bot.dao.AllMessage;
import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.gdpl2112.dg_bot.mapper.SaveMapper;
import io.github.gdpl2112.dg_bot.service.*;
import io.github.kloping.MySpringTool.interfaces.Logger;
import net.mamoe.mirai.console.plugin.PluginManager;
import net.mamoe.mirai.console.terminal.MiraiConsoleImplementationTerminal;
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.BotOnlineEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author github-kloping
 * @date 2023-07-17
 */
@Component
public class MiraiComponent extends SimpleListenerHost implements CommandLineRunner {
    @Autowired
    CronService service0;
    @Autowired
    PassiveService service1;
    @Autowired
    DefaultService defaultService;
    @Autowired
    ThreadPoolTaskExecutor executor;
    @Autowired
    AuthMapper authMapper;
    @Autowired
    Logger logger;
    @Autowired
    SaveService saveService;
    @Autowired
    ScriptService scriptService;
    @Autowired
    CallApiService callApiService;

    @Override
    public void run(String... args) throws Exception {
        executor.submit(() -> {
            MiraiConsoleTerminalLoader.INSTANCE.startAsDaemon(new MiraiConsoleImplementationTerminal());
            PluginManager.INSTANCE.loadPlugin(BuiltPlugin.INSTANCE);
            PluginManager.INSTANCE.enablePlugin(BuiltPlugin.INSTANCE);
        });
        GlobalEventChannel.INSTANCE.registerListenerHost(service0);
        GlobalEventChannel.INSTANCE.registerListenerHost(service1);
        GlobalEventChannel.INSTANCE.registerListenerHost(defaultService);
        GlobalEventChannel.INSTANCE.registerListenerHost(saveService);
        GlobalEventChannel.INSTANCE.registerListenerHost(scriptService);
        GlobalEventChannel.INSTANCE.registerListenerHost(callApiService);
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
            auth.setExp(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 30);
            auth.setT0(System.currentTimeMillis());
            authMapper.insert(auth);
            logger.info(String.format("%s管理秘钥生成完成:%s", bid, auth.getAuth()));
        } else {
            auth.setT0(System.currentTimeMillis());
            authMapper.updateById(auth);
            logger.info(String.format("%s登录成功,管理秘钥:%s", bid, auth.getAuth()));
        }
    }

    @Autowired
    SaveMapper saveMapper;

    @Scheduled(cron = "0 0/30 * * * ?")
    public void deleteMsg() {
        long less = System.currentTimeMillis() - 1000L * 60 * 30;
        QueryWrapper<AllMessage> qw = new QueryWrapper<>();
        qw.le("time", less);
        saveMapper.delete(qw);
    }
}
