package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.built.ScriptCompile;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.dao.CronMessage;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.CronMapper;
import io.github.gdpl2112.dg_bot.service.listenerhosts.ScriptService;
import io.github.gdpl2112.dg_bot.service.script.ScriptManager;
import io.github.kloping.judge.Judge;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class CronService implements CommandLineRunner, SchedulingConfigurer {
    public final CronMapper mapper;
    final BotService service;
    private final Map<Integer, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();
    private ScheduledTaskRegistrar taskRegistrar;
    @Autowired
    RestTemplate template;
    @Autowired
    ScriptService scriptService;
    @Autowired
    ConfMapper confMapper;
    @Autowired
    ReportService reportService;

    public CronService(CronMapper mapper, BotService service) {
        this.mapper = mapper;
        this.service = service;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
    }

    @Override
    public void run(String... args) {
        log.info("正在加载cron任务");
        for (CronMessage msg : mapper.selectList(new QueryWrapper<>())) appendTask(msg);
        log.info("cron任务加载完成");
    }

    public List<CronMessage> getCronMessages(long bid) {
        return mapper.selectList(new QueryWrapper<CronMessage>().eq("qid", bid));
    }

    private String getScriptCode(long bid) {
        Conf conf = confMapper.selectById(bid);
        return conf == null || Judge.isEmpty(conf.getCode()) ? null : conf.getCode();
    }

    public int appendTask(CronMessage msg) {
        String cron = msg.getCron();
        if (cron.split(" ").length == 7) {
            cron = cron.substring(0, cron.length() - 2);
        }
        ScheduledTask task = taskRegistrar.scheduleCronTask(new CronTask(() -> {
            ReentrantLock lock = accountLocks.computeIfAbsent(msg.getQid(), key -> new ReentrantLock());
            lock.lock();
            try {
                log.info("开始执行{} => {} cron任务", msg.getQid(), msg.getTargetId());
                if (msg.getTargetId().endsWith("FUNCTION") || msg.getTargetId().endsWith("function")) {
                    long bid = Long.parseLong(msg.getQid());
                    Bot bot = Bot.getInstanceOrNull(bid);
                    if (bot == null || !bot.isOnline()) {
                        log.warn("{} 用户实例获取失败! 可能掉线或未登录", bid);
                        reportService.report(String.valueOf(bid), "cron任务执行失败! 用户实例获取失败! 可能掉线或未登录");
                    } else {
                        ScriptCompile scriptCompile = scriptService.getJsEngine(bid);
                        if (scriptCompile != null) {
                            try {
                                scriptCompile.executeFuc(msg.getMsg());
                            } catch (Throwable e) {
                                ScriptManager.onException(bid, e);
                            }
                        }
                    }
                } else {
                    service.send(msg.getQid(), msg.getTargetId(), msg.getMsg());
                }
                log.info("执行{} => {} cron任务结束", msg.getQid(), msg.getTargetId());
            } finally {
                lock.unlock();
            }
        }, cron));

        tasks.put(msg.getId(), task);
        log.info("(id.{})添加{} => {} cron任务 ({})", msg.getId(), msg.getQid(), msg.getTargetId(), msg.getCron());
        return msg.getId();
    }

    public void del(String id) {
        try {
            mapper.deleteById(id);
            Integer taskId = Integer.parseInt(id);
            ScheduledTask task = tasks.remove(taskId);
            if (task != null) task.cancel(false);
            log.warn("删除并停止cron任务(id.{})", taskId);
        } catch (Exception e) {
            log.error("删除cron任务时出错", e);
        }
    }
}
