package io.github.gdpl2112.dg_bot.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务层 WebSocket 看门狗
 * <p>
 * 用于解决 NapCat 反向 WebSocket 长时间无消息导致的静默假死问题:
 * 底层 TCP 连接未断开, 但对端已不再推送任何事件, 协议层心跳检测无法感知。
 * <p>
 * 工作原理:
 * <ol>
 *     <li>业务层每收到任意事件(含心跳)时调用 {@link #updateActiveTime()} 原子更新活跃时间</li>
 *     <li>后台定时任务按 checkIntervalSeconds 周期检查空闲时长</li>
 *     <li>空闲超过 idleTimeoutSeconds 判定假死, 通过 {@link ConnectionHandle} 强制断开并重连</li>
 *     <li>重连成功后由外部回调 {@link #notifyReconnected()} 重置计时器并释放重连锁</li>
 * </ol>
 *
 * @author github kloping
 * @since 2026/07/26
 */
@Slf4j
public class WebSocketWatchdog {

    /**
     * 连接控制扩展点, 由现有 WebSocket 客户端实现
     */
    public interface ConnectionHandle {
        /**
         * 关闭当前连接; 允许抛出异常, 看门狗会捕获并继续执行重连
         */
        void close() throws Exception;

        /**
         * 触发重连; 可同步可异步实现,
         * 重连成功后必须回调 {@link WebSocketWatchdog#notifyReconnected()} 释放重连锁
         */
        void reconnect() throws Exception;
    }

    private final String name;
    private final long idleTimeoutMillis;
    private final long checkIntervalMillis;
    private final long reconnectTimeoutMillis;
    private final ConnectionHandle handle;
    private final ScheduledExecutorService scheduler;
    private final boolean ownScheduler;

    /** 最后活跃时间, 收到任意事件时更新 */
    private final AtomicLong lastActiveTime = new AtomicLong(System.currentTimeMillis());
    /** 重连锁, 防止多个监控周期或并发异常触发重复重连 */
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    /** 本次重连开始时间, 用于重连卡死保护 */
    private volatile long reconnectStartTime = 0L;
    private volatile ScheduledFuture<?> future;

    public WebSocketWatchdog(String name, long idleTimeoutSeconds, long checkIntervalSeconds,
                             long reconnectTimeoutSeconds, ConnectionHandle handle) {
        this(name, idleTimeoutSeconds, checkIntervalSeconds, reconnectTimeoutSeconds, handle, null);
    }

    /**
     * @param name                    连接标识(仅用于日志)
     * @param idleTimeoutSeconds      空闲超时阈值(秒), 超过判定假死
     * @param checkIntervalSeconds    后台检查间隔(秒)
     * @param reconnectTimeoutSeconds 重连锁保护超时(秒), 重连超过该时长仍未成功则强制释放锁等待下轮重试
     * @param handle                  连接控制扩展点
     * @param sharedScheduler         可共享的调度器; 传 null 则内部自建单线程调度器
     */
    public WebSocketWatchdog(String name, long idleTimeoutSeconds, long checkIntervalSeconds,
                             long reconnectTimeoutSeconds, ConnectionHandle handle,
                             ScheduledExecutorService sharedScheduler) {
        this.name = name;
        this.idleTimeoutMillis = TimeUnit.SECONDS.toMillis(idleTimeoutSeconds);
        this.checkIntervalMillis = TimeUnit.SECONDS.toMillis(checkIntervalSeconds);
        this.reconnectTimeoutMillis = TimeUnit.SECONDS.toMillis(reconnectTimeoutSeconds);
        this.handle = handle;
        if (sharedScheduler == null) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "watchdog-" + name);
                t.setDaemon(true);
                return t;
            });
            this.ownScheduler = true;
        } else {
            this.scheduler = sharedScheduler;
            this.ownScheduler = false;
        }
    }

    /**
     * 启动后台监控, 重复调用无副作用
     */
    public synchronized void start() {
        if (future != null && !future.isCancelled()) return;
        lastActiveTime.set(System.currentTimeMillis());
        future = scheduler.scheduleWithFixedDelay(this::safeCheck,
                checkIntervalMillis, checkIntervalMillis, TimeUnit.MILLISECONDS);
        log.info("[Watchdog:{}] 已启动, 空闲超时 {} 秒, 检查间隔 {} 秒",
                name, idleTimeoutMillis / 1000, checkIntervalMillis / 1000);
    }

    /**
     * 停止监控
     */
    public synchronized void stop() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
        if (ownScheduler) scheduler.shutdownNow();
        log.info("[Watchdog:{}] 已停止", name);
    }

    /**
     * onMessage 收到任意事件(包括底层心跳包)时调用, 原子更新活跃时间
     */
    public void updateActiveTime() {
        lastActiveTime.set(System.currentTimeMillis());
    }

    /**
     * 重连成功后调用: 重置计时器并释放重连锁
     */
    public void notifyReconnected() {
        lastActiveTime.set(System.currentTimeMillis());
        if (reconnecting.compareAndSet(true, false)) {
            log.info("[Watchdog:{}] 重连成功，重置计时器", name);
        }
    }

    /**
     * @return 当前空闲秒数
     */
    public long getIdleSeconds() {
        return (System.currentTimeMillis() - lastActiveTime.get()) / 1000;
    }

    /**
     * @return 是否正处于看门狗发起的强制重连流程中
     */
    public boolean isReconnecting() {
        return reconnecting.get();
    }

    /**
     * 监控任务入口: 任何异常都在此捕获, 不允许打崩调度线程
     */
    private void safeCheck() {
        try {
            check();
        } catch (Throwable t) {
            log.error("[Watchdog:{}] 监控任务异常(已捕获, 不影响后续检查): {}", name, t.getMessage(), t);
        }
    }

    private void check() {
        long now = System.currentTimeMillis();
        // 重连进行中: 若超过保护时长仍未成功, 强制释放锁, 避免 close/connect 卡死导致看门狗永久失效
        if (reconnecting.get()) {
            if (now - reconnectStartTime > reconnectTimeoutMillis) {
                log.warn("[Watchdog:{}] 重连超过 {} 秒仍未成功, 强制释放重连锁, 等待下一轮重试",
                        name, reconnectTimeoutMillis / 1000);
                reconnecting.set(false);
            }
            return;
        }
        long idleMillis = now - lastActiveTime.get();
        if (idleMillis < idleTimeoutMillis) return;
        // CAS 抢锁, 防止并发重复重连
        if (!reconnecting.compareAndSet(false, true)) return;
        reconnectStartTime = now;
        log.warn("[Watchdog:{}] 检测到假死，空闲 {} 秒，正在强制重连...", name, idleMillis / 1000);
        try {
            handle.close();
        } catch (Throwable e) {
            // close 失败不阻断重连流程
            log.warn("[Watchdog:{}] close() 异常(忽略, 继续重连): {}", name, e.getMessage());
        }
        try {
            handle.reconnect();
        } catch (Throwable e) {
            log.error("[Watchdog:{}] reconnect() 触发异常, 释放重连锁待下轮重试: {}", name, e.getMessage(), e);
            reconnecting.set(false);
        }
    }
}
