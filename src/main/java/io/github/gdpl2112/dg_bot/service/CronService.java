package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.built.ScriptService;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.dao.CronMessage;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.CronMapper;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.common.Public;
import io.github.kloping.date.CronJob;
import io.github.kloping.date.CronUtils;
import io.github.kloping.judge.Judge;
import kotlin.coroutines.CoroutineContext;
import net.mamoe.mirai.Bot;
import org.jetbrains.annotations.NotNull;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;


/**
 * @author github.kloping
 */
@Service
public class CronService extends net.mamoe.mirai.event.SimpleListenerHost implements CommandLineRunner {
    final CronMapper mapper;
    final BotService service;
    final Logger logger;

    public CronService(CronMapper mapper, BotService service, Logger logger) {
        this.mapper = mapper;
        this.service = service;
        this.logger = logger;
    }

    @Override
    public void handleException(@NotNull CoroutineContext context, @NotNull Throwable exception) {
    }

    private final Map<String, CronMessage> bid2cm = new HashMap<>();
    private final Map<Integer, Integer> cm2cron = new HashMap<>();

    @Override
    public void run(String... args) throws Exception {
        logger.info("正在加载cron任务");
        List<CronMessage> msgs = mapper.selectList(new QueryWrapper<>());
        for (CronMessage msg : msgs) {
            appendTask(msg);
        }
        logger.info("cron任务加载完成");
    }

    @Autowired
    RestTemplate template;

    @Autowired
    ScriptService scriptService;

    public int appendTask(CronMessage msg) {
        Integer id = addCronJob(msg.getCron(), new Job() {
            @Override
            public void execute(JobExecutionContext context) throws JobExecutionException {
                logger.log(String.format("开始执行%s => %s cron任务", msg.getQid(), msg.getTargetId()));
                if (msg.getTargetId().endsWith("FUNCTION") || msg.getTargetId().endsWith("function")) {
                    long bid = Long.parseLong(msg.getQid());
                    Bot bot = Bot.getInstanceOrNull(bid);
                    if (bot == null) {
                        logger.waring(String.format("%s 用户实例获取失败! 可能掉线或未登录", bid));
                    } else {
                        ScriptEngine JS_ENGINE = scriptService.getJsEngine(bid);
                        if (JS_ENGINE != null) Public.EXECUTOR_SERVICE.submit(() -> {
                            try {
                                if (JS_ENGINE instanceof Invocable) {
                                    Invocable inv = (Invocable) JS_ENGINE;
                                    inv.invokeFunction(msg.getMsg(), Bot.getInstanceOrNull(bid), ScriptService.getScriptUtils(bid));
                                }
                            } catch (Throwable e) {
                                ScriptService.onException(bid, e);
                            }
                        });
                    }
                } else {
                    service.send(msg.getQid(), msg.getTargetId(), msg.getMsg());
                }
                logger.log(String.format("执行%s => %s cron任务结束", msg.getQid(), msg.getTargetId()));
            }
        }, msg.getQid());
        bid2cm.put(msg.getQid(), msg);
        cm2cron.put(msg.getId(), id);
        return id;
    }

    @Autowired
    ConfMapper confMapper;

    private String getScriptCode(long bid) {
        Conf conf = confMapper.selectById(bid);
        if (conf == null) return null;
        if (Judge.isEmpty(conf.getCode())) return null;
        return conf.getCode();
    }

    public void del(String id) {
        try {
            int cid = cm2cron.get(Integer.parseInt(id));
            stop(cid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    static JobBuilder jobBuilder;
    static Scheduler scheduler;

    static {
        try {
            Properties props = new Properties();
            props.put("org.quartz.scheduler.instanceName", "kloping-cron-all");
            props.put("org.quartz.threadPool.threadCount", "3");
            CronUtils.SCHEDULER_FACTORY.initialize(props);
            scheduler = CronUtils.SCHEDULER_FACTORY.getScheduler();
            jobBuilder = JobBuilder.newJob(CronJob.class);
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    public synchronized Integer addCronJob(String cron, Job job, String name) {
        try {
            int id = getId();
            jobBuilder.withIdentity(name + "-cron-" + id, "default-group-all");
            JobDataMap map = new JobDataMap();
            map.put("job", job);
            jobBuilder.setJobData(map);
            JobDetail jobDetail = jobBuilder.build();
            CronTrigger cronTrigger = TriggerBuilder.newTrigger().withIdentity("default-name-" + id, "default-group-all").startNow().withSchedule(CronScheduleBuilder.cronSchedule(cron)).build();
            scheduler.scheduleJob(jobDetail, cronTrigger);
            scheduler.start();
            id2Scheduler.put(id, scheduler);
            return id;
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public Scheduler stop(Integer id) {
        if (id2Scheduler.containsKey(id)) {
            Scheduler scheduler = id2Scheduler.get(id);
            try {
                scheduler.shutdown(false);
            } catch (SchedulerException e) {
                e.printStackTrace();
            }
            return scheduler;
        }
        return null;
    }

    private static int id = 0;

    public static synchronized Integer getId() {
        return ++id;
    }

    public Map<Integer, Scheduler> id2Scheduler = new HashMap<>();
}
