package io.github.gdpl2112.dg_bot.built;

import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.service.script.BaseMessageScriptContext;
import io.github.gdpl2112.dg_bot.service.script.BaseScriptUtils;
import io.github.gdpl2112.dg_bot.service.script.ScriptContext;
import io.github.kloping.common.Public;
import io.github.kloping.judge.Judge;
import io.github.kloping.url.UrlUtils;
import kotlin.coroutines.CoroutineContext;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.code.MiraiCode;
import net.mamoe.mirai.message.data.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author github.kloping
 */
@Service
public class ScriptService extends SimpleListenerHost {
    public ScriptService() {
        System.setProperty("nashorn.args", "--no-deprecation-warning");
    }

    @Override
    public void handleException(@NotNull CoroutineContext context, @NotNull Throwable exception) {
        exception.printStackTrace();
    }

    public static final ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();

    @Autowired
    ConfMapper confMapper;

    @Autowired
    RestTemplate template;

    private String getScriptCode(long bid) {
        Conf conf = confMapper.selectById(bid);
        if (conf == null) return null;
        if (Judge.isEmpty(conf.getCode())) return null;
        return conf.getCode();
    }

    @EventHandler
    public void onMessage(@NotNull MessageEvent event) {
        if (event instanceof MessagePreSendEvent) return;
        if (event instanceof MessagePostSendEvent) return;
        final String code = getScriptCode(event.getBot().getId());
        if (code == null) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                ScriptEngine javaScript = SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                javaScript.put("context", new BaseMessageScriptContext(event));
                javaScript.put("utils", new BaseScriptUtils(event.getBot().getId(), template));
                String msg = toMsg(event.getMessage());
                javaScript.put("msg", msg);
                javaScript.eval(code);
            } catch (Throwable e) {
                onException(event.getBot(), e);
            }
        });
    }

    @EventHandler
    public void onEvent(BotEvent event) {
        if (event instanceof MessageEvent) return;
        if (event instanceof MessagePreSendEvent) return;
        if (event instanceof MessagePostSendEvent) return;
        final String code = getScriptCode(event.getBot().getId());
        if (code == null) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                ScriptEngine javaScript = SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                javaScript.put("context", new BasebBotEventScriptContext(event));
                javaScript.put("event", event);
                javaScript.put("utils", new BaseScriptUtils(event.getBot().getId(), template));
                javaScript.put("msg", event.toString());
                javaScript.eval(code);
            } catch (Throwable e) {
                onException(event.getBot(), e);
            }
        });
    }

    public Map<String, ScriptException> exceptionMap = new HashMap<>();

    private void onException(Bot bot, Throwable e) {
        e.printStackTrace();
        String err = Utils.getExceptionLine(e);
        err = e + err;
        Long bid = bot.getId();
        ScriptException se = new ScriptException(err, System.currentTimeMillis(), bid);
        exceptionMap.put(bid.toString(), se);
        System.err.println(String.format("%s Bot 脚本 执行失败", bot.getId()));
    }

    private String toMsg(MessageChain chain) {
        if (chain.size() == 2) {
            if (chain.get(1) instanceof PlainText) {
                return ((PlainText) chain.get(1)).getContent();
            }
        }
        return MiraiCode.serializeToMiraiCode((Iterable<? extends Message>) chain);
    }

    public static final Map<Long, Map<String, Object>> BID_2_VARIABLES = new HashMap<>();


    public static class BasebBotEventScriptContext implements ScriptContext {
        private BotEvent event;

        public BasebBotEventScriptContext(BotEvent userEvent) {
            this.event = userEvent;
        }

        @Override
        public Bot getBot() {
            return event.getBot();
        }

        @Override
        public void send(String str) {

        }

        @Override
        public void send(Message message) {

        }

        @Override
        public ForwardMessageBuilder forwardBuilder() {
            return new ForwardMessageBuilder(event.getBot().getAsFriend());
        }

        @Override
        public User getSender() {
            return null;
        }

        @Override
        public Contact getSubject() {
            return null;
        }

        @Override
        public Image uploadImage(String url) {
            try {
                byte[] bytes = UrlUtils.getBytesFromHttpUrl(url);
                Image image = Contact.uploadImage(event.getBot().getAsFriend(), new ByteArrayInputStream(bytes));
                return image;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public String getType() {
            return event.getClass().getSimpleName();
        }
    }

    public static class ScriptException {
        private String msg;
        private Long time;
        private Long qid;

        public ScriptException(String msg, Long time, Long qid) {
            this.msg = msg;
            this.time = time;
            this.qid = qid;
        }

        public String getMsg() {
            return msg;
        }

        public Long getTime() {
            return time;
        }

        public Long getQid() {
            return qid;
        }
    }
}
