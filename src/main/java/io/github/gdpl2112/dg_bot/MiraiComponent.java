package io.github.gdpl2112.dg_bot;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.AllMessage;
import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.gdpl2112.dg_bot.mapper.SaveMapper;
import io.github.gdpl2112.dg_bot.service.listenerhosts.*;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.common.Public;
import io.github.kloping.file.FileUtils;
import net.mamoe.mirai.console.command.CommandExecuteResult;
import net.mamoe.mirai.console.command.CommandManager;
import net.mamoe.mirai.console.command.ConsoleCommandSender;
import net.mamoe.mirai.console.terminal.MiraiConsoleImplementationTerminal;
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.BotOnlineEvent;
import net.mamoe.mirai.message.data.PlainText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static io.github.gdpl2112.dg_bot.compile.CompileRes.VERSION_DATE;

/**
 * @author github-kloping
 * @since 2023-07-17
 */
@Component
public class MiraiComponent extends SimpleListenerHost implements CommandLineRunner {
    @Autowired
    AuthMapper authMapper;
    @Autowired
    public Logger log;
    @Autowired
    ThreadPoolTaskExecutor executor;
    @Autowired
    PassiveService passiveService;
    @Autowired
    DefaultService defaultService;
    @Autowired
    SaveService saveService;
    @Autowired
    ScriptService scriptService;
    @Autowired
    CallApiService callApiService;
    @Autowired
    OptionalService optionalService;
    @Autowired
    SettingService settingService;

    private MiraiConsoleImplementationTerminal terminal = new MiraiConsoleImplementationTerminal();

    @Override
    public void run(String... args) throws Exception {
        System.setProperty("overflow.timeout", "20000");
        MiraiConsoleTerminalLoader.INSTANCE.startAsDaemon(terminal);
        GlobalEventChannel.INSTANCE.registerListenerHost(passiveService);
        GlobalEventChannel.INSTANCE.registerListenerHost(defaultService);
        GlobalEventChannel.INSTANCE.registerListenerHost(saveService);
        GlobalEventChannel.INSTANCE.registerListenerHost(scriptService);
        GlobalEventChannel.INSTANCE.registerListenerHost(callApiService);
        GlobalEventChannel.INSTANCE.registerListenerHost(optionalService);
        GlobalEventChannel.INSTANCE.registerListenerHost(settingService);
        GlobalEventChannel.INSTANCE.registerListenerHost(this);
    }

    @EventHandler
    public void onBotOnline(BotOnlineEvent event) {
        Long bid = event.getBot().getId();
        AuthM auth = authMapper.selectById(bid);
        if (auth == null) {
            log.info(String.format("%s登录成功,正在生成管理秘钥", bid));
            auth = new AuthM();
            auth.setQid(bid.toString());
            auth.setAuth(UUID.randomUUID().toString());
            auth.setExp(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 30);
            auth.setT0(System.currentTimeMillis());
            authMapper.insert(auth);
            log.info(String.format("%s管理秘钥生成完成:%s", bid, auth.getAuth()));
        } else {
            if (auth.getExp() < System.currentTimeMillis()) {
                event.getBot().close();
                log.error(String.format("%s已到期,强制下线", bid));
            } else {
                auth.setT0(System.currentTimeMillis());
                authMapper.updateById(auth);
                log.info(String.format("%s登录成功,管理秘钥:%s", bid, auth.getAuth()));
            }
        }
        if (event.getBot() instanceof RemoteBot) {
            RemoteBot remoteBot = (RemoteBot) event.getBot();
            String data = remoteBot.executeAction("get_stranger_info", "{\"user_id\": \"" + bid + "\"}");
            JSONObject jsonObject = JSONObject.parseObject(data);
            JSONObject jdata = jsonObject.getJSONObject("data");
            Boolean isVip = jdata.getBoolean("is_vip");
            VIP_INFO.put(bid, isVip);
        }
    }

    public static Map<Long, Boolean> VIP_INFO = new java.util.HashMap<>();

    @Autowired
    SaveMapper saveMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 */5 * * * ?")
    public void deleteMsg() {
        long less = System.currentTimeMillis() - 1000L * 30 * 120;
        QueryWrapper<AllMessage> qw = new QueryWrapper<>();
        qw.le("time", less);
        jdbcTemplate.execute("VACUUM;");
        log.info("释放db存储并删除消息记录: " + saveMapper.delete(qw));
    }

    @EventHandler
    public void onStartupEvent(net.mamoe.mirai.console.events.StartupEvent event) {
        System.out.println("Q云代挂启动成功 update at " + VERSION_DATE);
        Public.EXECUTOR_SERVICE1.submit(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
                String[] lines = FileUtils.getStringsFromFile("./after.line");
                if (lines != null) {
                    for (String line : lines) {
                        log.log("执行pre: " + line);
                        CountDownLatch cdl = new CountDownLatch(1);
                        EXECUTOR_SERVICE.submit(() -> {
                            CommandExecuteResult result = CommandManager.INSTANCE.executeCommand(ConsoleCommandSender.INSTANCE, new PlainText(line), false);
                            if (result instanceof CommandExecuteResult.Success) {
                                log.info("执行成功:" + line);
                            }
                            cdl.countDown();
                        });
                        try {
                            boolean k = cdl.await(75, TimeUnit.SECONDS);
                            if (!k) log.error("执行等待超时: " + line);
                        } catch (InterruptedException e) {
                            log.waring("执行等待报错:" + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }


    public static ExecutorService EXECUTOR_SERVICE = new ThreadPoolExecutor(15, 15, 5, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>());
}
