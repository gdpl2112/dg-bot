package io.github.gdpl2112.dg_bot;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.AllMessage;
import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.dao.ConnConfig;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.gdpl2112.dg_bot.mapper.ConnConfigMapper;
import io.github.gdpl2112.dg_bot.mapper.SaveMapper;
import io.github.gdpl2112.dg_bot.service.listenerhosts.*;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.BotOnlineEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import top.mrxiaom.overflow.BotBuilder;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.github.gdpl2112.dg_bot.compile.CompileRes.VERSION_DATE;

/**
 * @author github-kloping
 * @since 2023-07-17
 */
@Component
@Slf4j
public class MiraiComponent extends SimpleListenerHost implements CommandLineRunner {
    @Autowired
    AuthMapper authMapper;
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

    @Override
    public void run(String... args) throws Exception {
//        System.setProperty("overflow.timeout", "20000");
//        MiraiConsoleTerminalLoader.INSTANCE.startAsDaemon(terminal);
        GlobalEventChannel.INSTANCE.registerListenerHost(passiveService);
        GlobalEventChannel.INSTANCE.registerListenerHost(defaultService);
        GlobalEventChannel.INSTANCE.registerListenerHost(saveService);
        GlobalEventChannel.INSTANCE.registerListenerHost(scriptService);
        GlobalEventChannel.INSTANCE.registerListenerHost(callApiService);
        GlobalEventChannel.INSTANCE.registerListenerHost(optionalService);
        GlobalEventChannel.INSTANCE.registerListenerHost(settingService);
        GlobalEventChannel.INSTANCE.registerListenerHost(this);
        QueryWrapper<ConnConfig> qw = new QueryWrapper<>();
        qw.orderByAsc("qid");
        List<ConnConfig> connConfigs = connConfigMapper.selectList(qw);
//        AtomicInteger i = new AtomicInteger(1);
//        CountDownLatch cdl = new CountDownLatch(connConfigs.size());
//        connConfigs.forEach(r -> EXECUTOR_SERVICE.execute(() -> {
//            try {
//                TimeUnit.SECONDS.sleep(i.getAndIncrement());
//                handleOneBot(r);
//            } catch (Exception e) {
//                log.error("handle bot {} error", r.getQid(), e);
//            } finally {
//                cdl.countDown();
//            }
//        }));
//        cdl.await();
        for (int i = 0; i < connConfigs.size(); i++) {
            ConnConfig r = connConfigs.get(i);
            try {
                handleOneBot(r, i != 0);
            } catch (Exception e) {
                log.error("handle bot {} error", r.getQid(), e);
            }
        }
        System.out.println("Q云代挂启动成功 update at " + VERSION_DATE);
    }

    @Autowired
    ConnConfigMapper connConfigMapper;

    @EventHandler
    public void onBotOnline(BotOnlineEvent event) {
        Long bid = event.getBot().getId();
        AuthM auth = authMapper.selectById(bid);
        if (auth == null) {
            log.info("{}登录成功,正在生成管理秘钥", bid);
            auth = new AuthM();
            auth.setQid(bid.toString());
            auth.setAuth(UUID.randomUUID().toString());
            auth.setExp(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 30);
            auth.setT0(System.currentTimeMillis());
            authMapper.insert(auth);
            log.info("{}管理秘钥生成完成:{}", bid, auth.getAuth());
        } else {
            if (auth.getExp() < System.currentTimeMillis()) {
                event.getBot().close();
                log.error("{}已到期,强制下线", bid);
            } else {
                auth.setT0(System.currentTimeMillis());
                authMapper.updateById(auth);
                log.info("{}登录成功,管理秘钥:{}", bid, auth.getAuth());
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
        long less = System.currentTimeMillis() - (1000L * 60 * 30 * 3);
        QueryWrapper<AllMessage> qw = new QueryWrapper<>();
        qw.le("time", less);
        jdbcTemplate.execute("VACUUM;");
        log.info("释放db存储并删除消息记录: {}", saveMapper.delete(qw));
    }


    public static ExecutorService EXECUTOR_SERVICE = new ThreadPoolExecutor(20, 20, 10, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>());

    public static void handleOneBot(ConnConfig connConfig) {
        handleOneBot(connConfig, false);
    }

    public static void handleOneBot(ConnConfig connConfig, boolean tread) {
        BotBuilder builder = null;
        if (connConfig.getType().equalsIgnoreCase("ws")) {
            builder = BotBuilder.positive(connConfig.getIp()).retryTimes(3).retryWaitMills(7000).retryRestMills(-1);
        } else {
            builder = BotBuilder.reversed(connConfig.getPort());
        }
        builder.overrideLogger(log);
        builder.token(connConfig.getToken());
        builder.heartbeatCheckSeconds(connConfig.getHeart());

        if (builder != null) {
            if (tread) {
                BotBuilder finalBuilder = builder;
                EXECUTOR_SERVICE.execute(() -> {
                    try {
                        Bot bot = finalBuilder.connect();
                    } catch (Throwable e) {
                        log.error("on bot.{} connect error:{}", connConfig.getQid(), e.getMessage(), e);
                    }
                });
            } else {
                try {
                    Bot bot = builder.connect();
                } catch (Throwable e) {
                    log.error("on bot.{} connect error:{}", connConfig.getQid(), e.getMessage(), e);
                }
            }
        }
    }

    public static void closeOneBot(ConnConfig connConfig) {
        Bot bot = Bot.getInstanceOrNull(Long.valueOf(connConfig.getQid()));
        if (bot != null) {
            try {
                bot.close();
            } catch (Throwable e) {
                log.error("on bot.{} close error:{}", connConfig.getQid(), e.getMessage());
            }
        }
    }
}
