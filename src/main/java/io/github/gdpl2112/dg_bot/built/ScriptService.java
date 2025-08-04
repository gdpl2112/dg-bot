package io.github.gdpl2112.dg_bot.built;

import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.events.GroupSignEvent;
import io.github.gdpl2112.dg_bot.events.ProfileLikeEvent;
import io.github.gdpl2112.dg_bot.events.SendLikedEvent;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.SaveMapper;
import io.github.gdpl2112.dg_bot.service.ConfigService;
import io.github.gdpl2112.dg_bot.service.script.ScriptUtils;
import io.github.gdpl2112.dg_bot.service.script.impl.BaseScriptUtils;
import io.github.kloping.common.Public;
import io.github.kloping.judge.Judge;
import kotlin.coroutines.CoroutineContext;
import lombok.Getter;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.data.MessageChain;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
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

    public static final Map<Long, Map<String, Object>> BID_2_VARIABLES = new HashMap<>();

    public static final ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();

    @Autowired
    ConfMapper confMapper;

    @Autowired
    RestTemplate template;

    @Autowired
    SaveMapper saveMapper;

    @Autowired
    ConfigService configService;

    public static Map<Long, ScriptEngine> BID2ENGINE = new HashMap<>();

    public synchronized ScriptEngine getJsEngine(long bid) {
        if (BID2ENGINE.containsKey(bid)) return BID2ENGINE.get(bid);
        else {
            String code = getScriptCode(bid);
            if (code == null) {
                BID2ENGINE.put(bid, null);
                return null;
            } else {
                ScriptEngine engine = null;
                try {
                    engine = SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                    engine.eval(code);
                } catch (Exception e) {
                    onException(bid, e, "初始化JS脚本时报错");
                }
                BID2ENGINE.put(bid, engine);
                return engine;
            }
        }
    }

    private String toMsg(MessageChain chain) {
        String msg = DgSerializer.messageChainSerializeToString(chain);
        return msg;
    }

    public static Map<String, ScriptException> exceptionMap = new HashMap<>();

    private String getScriptCode(long bid) {
        Conf conf = confMapper.selectById(bid);
        if (conf == null) return null;
        if (Judge.isEmpty(conf.getCode())) return null;
        return conf.getCode();
    }

    private static final Map<Long, ScriptUtils> BID2UTILS = new HashMap<>();
    private static final RestTemplate TEMPLATE = new RestTemplate();

    public static ScriptUtils getScriptUtils(long bid) {
        if (BID2UTILS.containsKey(bid)) return BID2UTILS.get(bid);
        else {
            ScriptUtils utils = new BaseScriptUtils(bid, TEMPLATE);
            BID2UTILS.put(bid, utils);
            return utils;
        }
    }

    public static boolean isDefine(ScriptEngine engine, String funName) {
        try {
            Object k = engine.eval("typeof " + funName + " === 'function'");
            return Boolean.valueOf(k.toString());
        } catch (Exception e) {
            return false;
        }
    }

    public static void onException(Bot bot, Throwable e) {
        onException(bot.getId(), e);
    }

    public static void onException(long bid, Throwable e) {
        onException(bid, e, "");
    }

    public static void onException(long bid, Throwable e, String msg) {
        e.printStackTrace();
        String err = Utils.getExceptionLine(e);
        err = e + err;
        ScriptException se = new ScriptException(msg + "\n" + err, System.currentTimeMillis(), bid);
        exceptionMap.put(String.valueOf(bid), se);
        System.err.println(String.format("%s Bot 脚本 执行失败", bid));
    }

    @Getter
    public static class ScriptException {
        private String msg;
        private Long time;
        private Long qid;

        public ScriptException(String msg, Long time, Long qid) {
            this.msg = msg;
            this.time = time;
            this.qid = qid;
        }
    }

    //===================================
    // MessageEvent 事件入口 方法名
    private static final String ON_MSG_EVENT_FUNCTION = "onMsgEvent";
    // BotEvent 事件入口 方法名
    private static final String ON_BOT_EVENT_FUNCTION = "onBotEvent";
    // ProfileLikeEvent 事件入口 方法名
    private static final String ON_PROFILE_LIKE_FUNCTION = "onProfileLike";
    // SendLikedEvent 事件入口 方法名
    private static final String ON_SEND_LIKED_FUNCTION = "onSendLiked";
    // GroupSignEvent 事件入口 方法名
    private static final String ON_GROUP_SIGN_FUNCTION = "onGroupSign";

    @EventHandler
    public void onMessage(@NotNull MessageEvent event) {
        if (event instanceof MessagePreSendEvent) return;
        if (event instanceof MessagePostSendEvent) return;
        String tid = event instanceof GroupMessageEvent ? "g" + event.getSubject().getId() : event instanceof FriendMessageEvent ? "f" + event.getSender().getId() : null;
        if (tid != null) if (configService.isNotOpenK0(event.getBot().getId(), tid)) return;
        ScriptEngine JS_ENGINE = getJsEngine(event.getBot().getId());
        if (JS_ENGINE != null) Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                if (isDefine(JS_ENGINE, ON_MSG_EVENT_FUNCTION)) {
                    if (JS_ENGINE instanceof Invocable) {
                        Invocable inv = (Invocable) JS_ENGINE;
                        inv.invokeFunction(ON_MSG_EVENT_FUNCTION, toMsg(event.getMessage()), event, getScriptUtils(event.getBot().getId()));
                    }
                }
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
        if (event instanceof BotOnlineEvent) return;
        if (event instanceof BotOfflineEvent) return;
        ScriptEngine JS_ENGINE = getJsEngine(event.getBot().getId());
        if (JS_ENGINE != null) Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                if (isDefine(JS_ENGINE, ON_BOT_EVENT_FUNCTION)) {
                    if (JS_ENGINE instanceof Invocable) {
                        Invocable inv = (Invocable) JS_ENGINE;
                        inv.invokeFunction(ON_BOT_EVENT_FUNCTION, event, getScriptUtils(event.getBot().getId()));
                    }
                }
            } catch (Throwable e) {
                onException(event.getBot(), e);
            }
        });
    }

    @EventListener
    public void onEvent(ProfileLikeEvent event) {
        long bid = event.getSelfId();
        ScriptEngine JS_ENGINE = getJsEngine(bid);
        if (JS_ENGINE != null) Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                if (isDefine(JS_ENGINE, ON_PROFILE_LIKE_FUNCTION)) {
                    if (JS_ENGINE instanceof Invocable) {
                        Invocable inv = (Invocable) JS_ENGINE;
                        inv.invokeFunction(ON_PROFILE_LIKE_FUNCTION, event,getScriptUtils(bid), Bot.getInstanceOrNull(bid));
                    }
                }
            } catch (Throwable e) {
                onException(bid, e);
            }
        });
    }

    @EventListener
    public void onEvent(SendLikedEvent event) {
        long bid = event.getSelfId();
        ScriptEngine JS_ENGINE = getJsEngine(bid);
        if (JS_ENGINE != null) Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                if (isDefine(JS_ENGINE, ON_SEND_LIKED_FUNCTION)) {
                    if (JS_ENGINE instanceof Invocable) {
                        Invocable inv = (Invocable) JS_ENGINE;
                        inv.invokeFunction(ON_SEND_LIKED_FUNCTION, event,getScriptUtils(bid), Bot.getInstanceOrNull(bid));
                    }
                }
            } catch (Throwable e) {
                onException(bid, e);
            }
        });
    }

    @EventListener
    public void onEvent(GroupSignEvent event) {
        long bid = event.getSelfId();
        ScriptEngine JS_ENGINE = getJsEngine(bid);
        if (JS_ENGINE != null) Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                if (isDefine(JS_ENGINE, ON_GROUP_SIGN_FUNCTION)) {
                    if (JS_ENGINE instanceof Invocable) {
                        Invocable inv = (Invocable) JS_ENGINE;
                        inv.invokeFunction(ON_GROUP_SIGN_FUNCTION, event,getScriptUtils(bid), Bot.getInstanceOrNull(bid));
                    }
                }
            } catch (Throwable e) {
                onException(bid, e);
            }
        });
    }


}
