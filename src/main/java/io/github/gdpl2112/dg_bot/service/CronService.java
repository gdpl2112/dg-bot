package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.built.ScriptService;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.dao.CronMessage;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.CronMapper;
import io.github.gdpl2112.dg_bot.service.script.BaseMessageScriptContext;
import io.github.gdpl2112.dg_bot.service.script.BaseScriptUtils;
import io.github.gdpl2112.dg_bot.service.script.ScriptContext;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.date.CronUtils;
import io.github.kloping.judge.Judge;
import kotlin.coroutines.CoroutineContext;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.message.data.ForwardMessageBuilder;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.MessageChain;
import org.jetbrains.annotations.NotNull;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.script.ScriptEngine;
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
            appendTask(msg);
        }
        logger.info("cron任务加载完成");
    }

    @Autowired
    RestTemplate template;

    public int appendTask(CronMessage msg) {
        Integer id = CronUtils.INSTANCE.addCronJob(msg.getCron(), new Job() {
            @Override
            public void execute(JobExecutionContext context) throws JobExecutionException {
                logger.log(String.format("开始执行%s => %s cron任务", msg.getQid(), msg.getTargetId()));
                if (msg.getTargetId().endsWith("FUNCTION")) {
                    long bid = Long.parseLong(msg.getQid());
                    Bot bot = Bot.getInstanceOrNull(bid);
                    if (bot == null) {
                        logger.waring(String.format("%s 用户实例获取失败! 可能掉线或未登录", bid));
                    } else {
                        try {
                            ScriptEngine javaScript = ScriptService.SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                            javaScript.put("context", new ScriptContext() {
                                @Override
                                public Bot getBot() {
                                    return bot;
                                }

                                @Override
                                public MessageChain getRaw() {
                                    return null;
                                }

                                @Override
                                public void send(String str) {

                                }

                                @Override
                                public void send(Message message) {

                                }

                                @Override
                                public ForwardMessageBuilder forwardBuilder() {
                                    return new ForwardMessageBuilder(bot.getAsFriend());
                                }

                                @Override
                                public MessageChain getMessageChainById(int id) {
                                    return null;
                                }

                                @Override
                                public User getSender() {
                                    return bot.getAsFriend();
                                }

                                @Override
                                public Contact getSubject() {
                                    return bot.getAsFriend();
                                }

                                @Override
                                public String getType() {
                                    return "cron";
                                }
                            });
                            javaScript.put("utils", new BaseScriptUtils(bid, template));
                            javaScript.put("bot", bot);
                            final String code = getScriptCode(bid);
                            if (code == null) return;
                            javaScript.eval(code);
                            javaScript.eval(msg.getMsg());
                        } catch (Exception e) {
                            ScriptService.onException(bot, e);
                        }
                    }
                } else {
                    service.send(msg.getQid(), msg.getTargetId(), msg.getMsg());
                }
                logger.log(String.format("执行%s => %s cron任务结束", msg.getQid(), msg.getTargetId()));
            }
        });
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
            CronUtils.INSTANCE.stop(cid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
