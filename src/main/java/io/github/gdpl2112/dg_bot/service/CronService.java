package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.CronMessage;
import io.github.gdpl2112.dg_bot.mapper.CronMapper;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.date.CronUtils;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.NotNull;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            Integer id = CronUtils.INSTANCE.addCronJob(msg.getCron(), new Job() {
                @Override
                public void execute(JobExecutionContext context) throws JobExecutionException {
                    logger.log(String.format("开始执行%s => %s cron任务", msg.getQid(), msg.getTargetId()));
                    service.send(msg.getQid(), msg.getTargetId(), msg.getMsg());
                    logger.log(String.format("执行%s => %s cron任务结束", msg.getQid(), msg.getTargetId()));
                }
            });
            bid2cm.put(msg.getQid(), msg);
            cm2cron.put(msg.getId(), id);
        }
        logger.info("cron任务加载完成");
    }
}
