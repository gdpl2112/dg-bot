package io.github.gdpl2112.dg_bot.service;

import io.github.gdpl2112.dg_bot.MiraiComponent;
import io.github.gdpl2112.dg_bot.dao.ConnConfig;
import io.github.gdpl2112.dg_bot.mapper.ConnConfigMapper;
import io.github.gdpl2112.dg_bot.utils.WebSocketWatchdog;
import jakarta.annotation.PreDestroy;
import kotlin.coroutines.CoroutineContext;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.BotEvent;
import net.mamoe.mirai.event.events.BotOfflineEvent;
import net.mamoe.mirai.event.events.BotOnlineEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Bot 连接看门狗服务
 * <p>
 * 为每个 OneBot(NapCat) 连接挂载一条 {@link WebSocketWatchdog}:
 * 任意 Bot 事件到达即视为连接活跃; 空闲超过阈值判定假死,
 * 强制 close 后走 {@link MiraiComponent#handleOneBot} 重连,
 * 重连成功由 {@link BotOnlineEvent} 回调确认并重置计时器。
 * <p>
 * 所有 Bot 共用一条调度线程, 逐个检查, 互不影响。
 *
 * @author github kloping
 * @since 2026/07/26
 */
@Slf4j
@Service
public class BotWatchdogService extends SimpleListenerHost {

    /** 是否启用看门狗 */
    @Value("${watchdog.enabled:true}")
    Boolean enabled;
    /** 空闲超时阈值(秒), 超过判定假死, 默认 7200 秒(2小时) */
    @Value("${watchdog.idle-timeout-seconds:7200}")
    Long idleTimeoutSeconds;
    /** 后台检查间隔(秒), 默认 30 秒 */
    @Value("${watchdog.check-interval-seconds:30}")
    Long checkIntervalSeconds;
    /** 重连保护超时(秒), 重连超过该时长未成功则释放锁重试, 默认 180 秒 */
    @Value("${watchdog.reconnect-timeout-seconds:180}")
    Long reconnectTimeoutSeconds;
    /** 夜间暂停起始小时(含), 默认 23 点 */
    @Value("${watchdog.night-pause-start-hour:23}")
    Integer nightPauseStartHour;
    /** 夜间暂停结束小时(不含), 默认 7 点 */
    @Value("${watchdog.night-pause-end-hour:7}")
    Integer nightPauseEndHour;

    @Autowired
    ConnConfigMapper connConfigMapper;

    /** qid -> 看门狗实例 */
    private final Map<Long, WebSocketWatchdog> watchdogs = new ConcurrentHashMap<>();
    /** 全部看门狗共享的调度线程 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bot-watchdog");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void handleException(@NotNull CoroutineContext context, @NotNull Throwable exception) {
        log.error("[Watchdog] 事件处理异常", exception);
    }

    /**
     * Bot 上线: 创建/启动对应看门狗, 并作为重连成功信号重置计时器
     */
    @EventHandler
    public void onBotOnline(BotOnlineEvent event) {
        if (!enabled) return;
        long qid = event.getBot().getId();
        WebSocketWatchdog watchdog = watchdogs.computeIfAbsent(qid, this::createWatchdog);
        watchdog.start();
        watchdog.notifyReconnected();
    }

    /**
     * 主动下线(如到期强制下线)时停止看门狗, 避免误重连;
     * 被动掉线不停止, 交由看门狗/overflow 重连;
     * 看门狗自身发起的强制断开(取消连接 Job)也会广播 Active 下线,
     * 此时不能移除看门狗, 否则重连失败后无人再守护
     */
    @EventHandler
    public void onBotOffline(BotOfflineEvent.Active event) {
        long qid = event.getBot().getId();
        WebSocketWatchdog watchdog = watchdogs.get(qid);
        if (watchdog == null) return;
        if (watchdog.isReconnecting()) return;
        watchdogs.remove(qid);
        watchdog.stop();
    }

    /**
     * 任意 Bot 事件(消息/通知/心跳触达的事件)均视为连接活跃, 即"喂狗"
     */
    @EventHandler
    public void onAnyEvent(BotEvent event) {
        WebSocketWatchdog watchdog = watchdogs.get(event.getBot().getId());
        if (watchdog != null) watchdog.updateActiveTime();
    }

    private WebSocketWatchdog createWatchdog(long qid) {
        return new WebSocketWatchdog(String.valueOf(qid), idleTimeoutSeconds, checkIntervalSeconds,
                reconnectTimeoutSeconds, new WebSocketWatchdog.ConnectionHandle() {
            @Override
            public void close() {
                // 不能直接 bot.close(): overflow 内部走 channel.close(code, reason) 两参重载,
                // 不会置位 WSClient.scheduleClose, 旧连接会在 onClose 后内部自动重连, 断开不彻底;
                // 取消连接父 Job 才会回调 WSClient 无参 close(), 彻底断开且不触发内部重连,
                // 旧 BotWrapper 实例保留, 重连时由 overflow wrap() 复用并替换底层连接
                MiraiComponent.cancelConnJob(String.valueOf(qid));
                // 反向连接的端口监听不受连接 Job 管控, 需单独彻底关闭,
                // 否则旧假死客户端连接仍占用服务端, 重连时等不到新连接
                ConnConfig connConfig = connConfigMapper.selectById(String.valueOf(qid));
                if (connConfig != null && !"ws".equalsIgnoreCase(connConfig.getType())) {
                    MiraiComponent.closeReversedServer(connConfig.getPort());
                }
            }

            @Override
            public void reconnect() {
                ConnConfig connConfig = connConfigMapper.selectById(String.valueOf(qid));
                if (connConfig == null) {
                    // 连接配置已被删除, 无需再守护
                    log.warn("[Watchdog:{}] 未找到连接配置, 停止守护", qid);
                    WebSocketWatchdog watchdog = watchdogs.remove(qid);
                    if (watchdog != null) watchdog.stop();
                    return;
                }
                // 异步重连, 成功后由 onBotOnline -> notifyReconnected 释放重连锁
                MiraiComponent.handleOneBot(connConfig, true);
            }
        }, scheduler, this::isInNightPause);
    }

    /**
     * 夜间时段(默认 23:00-7:00)不做假死判定, 避免夜间无消息误判重连
     */
    private boolean isInNightPause() {
        int hour = LocalTime.now().getHour();
        if (nightPauseStartHour <= nightPauseEndHour) return hour >= nightPauseStartHour && hour < nightPauseEndHour;
        // 跨午夜: 如 23-7 表示 [23:00, 24:00) 和 [0:00, 7:00)
        return hour >= nightPauseStartHour || hour < nightPauseEndHour;
    }

    @PreDestroy
    public void cleanup() {
        watchdogs.values().forEach(WebSocketWatchdog::stop);
        watchdogs.clear();
        scheduler.shutdownNow();
    }
}
