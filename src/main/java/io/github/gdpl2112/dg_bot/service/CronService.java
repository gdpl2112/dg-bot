package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.built.ScriptCompile;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.dao.CronMessage;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.CronMapper;
import io.github.gdpl2112.dg_bot.service.listenerhosts.ScriptService;
import io.github.gdpl2112.dg_bot.service.script.ScriptManager;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.common.Public;
import io.github.kloping.date.CronUtils;
import io.github.kloping.judge.Judge;
import net.mamoe.mirai.Bot;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author github.kloping
 */
@Service
public class CronService implements CommandLineRunner {
    public final CronMapper mapper;
    final BotService service;
    final Logger logger;

    public CronService(CronMapper mapper, BotService service, Logger logger) {
        this.mapper = mapper;
        this.service = service;
        this.logger = logger;
    }

    private final Map<Integer, Integer> cmid2cronid = new HashMap<>();

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

    @Autowired
    ConfMapper confMapper;

    public List<CronMessage> getCronMessages(long bid) {
        return mapper.selectList(new QueryWrapper<CronMessage>().eq("qid", bid));
    }

    private String getScriptCode(long bid) {
        Conf conf = confMapper.selectById(bid);
        if (conf == null) return null;
        if (Judge.isEmpty(conf.getCode())) return null;
        return conf.getCode();
    }

    @Autowired
    ReportService reportService;

    public int appendTask(CronMessage msg) {
        Integer id = CronUtils.INSTANCE.addCronJob(msg.getCron(), new Job() {
            @Override
            public void execute(JobExecutionContext context) throws JobExecutionException {
                logger.log(String.format("开始执行%s => %s cron任务", msg.getQid(), msg.getTargetId()));
                if (msg.getTargetId().endsWith("FUNCTION") || msg.getTargetId().endsWith("function")) {
                    long bid = Long.parseLong(msg.getQid());
                    Bot bot = Bot.getInstanceOrNull(bid);
                    if (bot == null || !bot.isOnline()) {
                        logger.waring(String.format("%s 用户实例获取失败! 可能掉线或未登录", bid));
                        reportService.report(String.valueOf(bid), "cron任务执行失败! 用户实例获取失败! 可能掉线或未登录");
                    } else {
                        ScriptCompile scriptCompile = scriptService.getJsEngine(bid);
                        if (scriptCompile != null) Public.EXECUTOR_SERVICE.submit(() -> {
                            try {
                                scriptCompile.executeFuc(msg.getMsg());
                            } catch (Throwable e) {
                                ScriptManager.onException(bid, e);
                            }
                        });
                    }
                } else {
                    service.send(msg.getQid(), msg.getTargetId(), msg.getMsg());
                }
                logger.log(String.format("执行%s => %s cron任务结束", msg.getQid(), msg.getTargetId()));
            }
        });
        cmid2cronid.put(msg.getId(), id);
        logger.info(String.format("(id.%s)添加%s => %s cron任务 (%s)", id, msg.getQid(), msg.getTargetId(), msg.getCron()));
        return id;
    }

    public void del(String id) {
        try {
            mapper.deleteById(id);
            Integer aid = Integer.parseInt(id);
            int cid = cmid2cronid.remove(aid);
            CronUtils.INSTANCE.stop(cid);
            logger.waring(String.format("删除并停止cron任务(id.%s)", cid));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
