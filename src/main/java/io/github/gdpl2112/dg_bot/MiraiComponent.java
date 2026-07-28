package io.github.gdpl2112.dg_bot;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.AllMessage;
import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.dao.ConnConfig;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.gdpl2112.dg_bot.mapper.ConnConfigMapper;
import io.github.gdpl2112.dg_bot.mapper.SaveMapper;
import io.github.gdpl2112.dg_bot.service.BotWatchdogService;
import io.github.gdpl2112.dg_bot.service.listenerhosts.*;
import jakarta.annotation.PreDestroy;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.JobKt;
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
import java.util.concurrent.ConcurrentHashMap;
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
    public static Map<Long, Boolean> VIP_INFO = new java.util.HashMap<>();
    public static ExecutorService EXECUTOR_SERVICE = new ThreadPoolExecutor(20, 20, 10, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>());
    /**
     * qid -> 连接父 Job。
     * <p>
     * overflow 的 BotWrapper.close() 走的是 channel.close(code, reason) 两参重载,
     * 不会命中 WSClient 覆写的无参 close(), 导致 scheduleClose 标志未置位,
     * 旧 WSClient 会在 onClose 中触发内部 retry() 自动重连, 造成"断开不彻底";
     * 只有取消 parentJob 才会回调 WSClient 的无参 close() 实现彻底断开。
     */
    public static final Map<String, CompletableJob> CONN_JOBS = new ConcurrentHashMap<>();
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
    @Autowired
    GroupEventService groupEventService;
    @Autowired
    BotWatchdogService botWatchdogService;
    @Autowired
    ConnConfigMapper connConfigMapper;
    @Autowired
    SaveMapper saveMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public static void handleOneBot(ConnConfig connConfig) {
        handleOneBot(connConfig, false);
    }

    /**
     * 彻底断开指定 qid 的旧连接:
     * 取消连接父 Job 会同步触发 overflow WSClient 的无参 close(),
     * 置位 scheduleClose, 使旧连接关闭后不再内部自动重连
     */
    public static void cancelConnJob(String qid) {
        CompletableJob job = CONN_JOBS.remove(qid);
        if (job != null) {
            try {
                job.cancel(null);
            } catch (Throwable e) {
                log.warn("cancel bot.{} conn job error:{}", qid, e.getMessage());
            }
        }
    }

    /**
     * 反向连接模式下彻底关闭端口监听:
     * overflow 的 WSServer 不受 parentJob 管控, 取消连接 Job 不会停止端口监听,
     * 且 start0 会按端口复用缓存在 Overflow.reverseServerConfig 中的旧服务端,
     * 旧的假死客户端连接也不会被踢掉; 只能反射取出 OneBotProducer 调用 close()
     * (即 WSServer.stop(), 停止端口监听并断开全部客户端连接)
     */
    public static void closeReversedServer(int port) {
        try {
            Object producer = reverseServers().remove(port);
            if (producer == null) return;
            Class.forName("cn.evolvefield.onebot.client.connection.OneBotProducer")
                    .getMethod("close").invoke(producer);
            log.info("已彻底关闭反向 WebSocket 端口 {} 的监听", port);
        } catch (Throwable e) {
            log.warn("关闭反向 WebSocket 端口 {} 监听失败:{}", port, e.getMessage());
        }
    }

    /**
     * 关闭全部反向 WebSocket 端口监听(应用关闭前清理用)
     */
    public static void closeAllReversedServers() {
        try {
            for (Object port : new java.util.ArrayList<>(reverseServers().keySet())) {
                closeReversedServer((Integer) port);
            }
        } catch (Throwable e) {
            log.warn("关闭全部反向 WebSocket 端口监听失败:{}", e.getMessage());
        }
    }

    /**
     * 反射获取 Overflow 内部按端口缓存的反向 WebSocket 服务端表(port -> OneBotProducer);
     * Overflow 尚未初始化(首次启动时)返回空表
     */
    @SuppressWarnings("unchecked")
    private static Map<Object, Object> reverseServers() throws Exception {
        Class<?> clazz = Class.forName("top.mrxiaom.overflow.internal.Overflow");
        java.lang.reflect.Field instanceField = clazz.getDeclaredField("_instance");
        instanceField.setAccessible(true);
        Object overflow = instanceField.get(null);
        if (overflow == null) return new java.util.HashMap<>();
        java.lang.reflect.Field field = clazz.getDeclaredField("reverseServerConfig");
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(overflow);
    }

    public static void handleOneBot(ConnConfig connConfig, boolean tread) {
        BotBuilder builder = null;
        if (connConfig.getType().equalsIgnoreCase("ws")) {
            builder = BotBuilder.positive(connConfig.getIp()).retryTimes(3).retryWaitMills(7000).retryRestMills(-1);
        } else {
            // 先彻底关闭旧的端口监听, 踢掉可能残留的假死客户端连接,
            // 避免 overflow 复用旧 WSServer 后 awaitNewBotConnection 一直等不到新连接
            closeReversedServer(connConfig.getPort());
            builder = BotBuilder.reversed(connConfig.getPort());
        }
        builder.overrideLogger(log);
        builder.token(connConfig.getToken());
        builder.heartbeatCheckSeconds(connConfig.getHeart());
        // 建新连接前先取消旧连接 Job, 防止新旧两条连接并存(wrap 会抛"一个账号只允许接入一条连接")
        cancelConnJob(connConfig.getQid());
        CompletableJob connJob = JobKt.Job(null);
        CONN_JOBS.put(connConfig.getQid(), connJob);
        builder.parentJob(connJob);

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
        // 先取消连接 Job 彻底关闭底层 WSClient(不触发其内部自动重连), 再关闭 Bot 实例
        cancelConnJob(connConfig.getQid());
        // 反向连接的端口监听不受连接 Job 管控, 需要单独彻底关闭
        if (!"ws".equalsIgnoreCase(connConfig.getType())) {
            closeReversedServer(connConfig.getPort());
        }
        Bot bot = Bot.getInstanceOrNull(Long.valueOf(connConfig.getQid()));
        if (bot != null) {
            try {
                bot.close();
            } catch (Throwable e) {
                log.error("on bot.{} close error:{}", connConfig.getQid(), e.getMessage());
            }
        }
    }

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
        GlobalEventChannel.INSTANCE.registerListenerHost(groupEventService);
        GlobalEventChannel.INSTANCE.registerListenerHost(botWatchdogService);
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
        // 排序 configs
        // 优先将189开头的排在前面,其他按id升序
        connConfigs.sort((o1, o2) -> {
            if (o1.getQid().startsWith("189") && !o2.getQid().startsWith("189")) return -1;
            return o1.getQid().compareTo(o2.getQid());
        });
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
                // 先取消连接 Job, 避免 close 后底层 WSClient 内部自动重连
                cancelConnJob(bid.toString());
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

    @Scheduled(cron = "0 */5 * * * ?")
    public void deleteMsg() {
        long less = System.currentTimeMillis() - (1000L * 60 * 30 * 3);
        QueryWrapper<AllMessage> qw = new QueryWrapper<>();
        qw.le("time", less);
        jdbcTemplate.execute("VACUUM;");
        log.info("释放db存储并删除消息记录: {}", saveMapper.delete(qw));
    }


    /**
     * 应用程序关闭前执行的清理操作
     */
    @PreDestroy
    public void cleanup() {
        log.info("应用程序正在关闭，执行清理操作...");
        CONN_JOBS.keySet().forEach(MiraiComponent::cancelConnJob);
        closeAllReversedServers();
        Bot.getInstances().forEach(e -> {
            try {
                e.close();
            } catch (Exception ex) {
                log.error("关闭bot {} error", e.getId(), ex);
            }
        });
        log.info("清理操作完成");
    }

}
