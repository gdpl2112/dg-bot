package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.built.ScriptCompile;
import io.github.gdpl2112.dg_bot.dao.AllMessage;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.events.GroupSignEvent;
import io.github.gdpl2112.dg_bot.events.ProfileLikeEvent;
import io.github.gdpl2112.dg_bot.events.SendLikedEvent;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.SaveMapper;
import io.github.gdpl2112.dg_bot.service.script.ScriptManager;
import io.github.gdpl2112.dg_bot.service.script.impl.BaseScriptUtils;
import io.github.kloping.common.Public;
import io.github.kloping.judge.Judge;
import kotlin.coroutines.CoroutineContext;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.contact.UserOrBot;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.data.MessageChain;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import static io.github.gdpl2112.dg_bot.service.script.ScriptManager.*;

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

    @Autowired
    ConfMapper confMapper;

    @Autowired
    RestTemplate template;

    @Autowired
    SaveMapper saveMapper;

    @Autowired
    ConfigService configService;


    private String toMsg(MessageChain chain) {
        String msg = DgSerializer.messageChainSerializeToString(chain);
        return msg;
    }

    private String getScriptCode(long bid) {
        Conf conf = confMapper.selectById(bid);
        if (conf == null) return null;
        if (Judge.isEmpty(conf.getCode())) return null;
        return conf.getCode();
    }

    public synchronized ScriptCompile getJsEngine(long bid) {
        if (BID2ENGINE.containsKey(bid)) return BID2ENGINE.get(bid);
        else {
            String code = getScriptCode(bid);
            if (code == null) {
                BID2ENGINE.put(bid, null);
                return null;
            } else {
                ScriptCompile scriptCompile = null;
                Map<String, Object> objectMap = new HashMap<>();
                objectMap.put("utils", getScriptUtils(bid));
                objectMap.put("bot", Bot.getInstance(bid));
                ScriptManager.Logger logger = new ScriptManager.Logger() {
                    private String key = String.valueOf(bid);

                    @Override
                    public void log(String msg) {
                        synchronized (this) {
                            System.out.println("from js(" + bid + ") " + msg);
                            offerLogMsg(key, msg);
                        }
                    }

                    @Override
                    public void log(String msg, Object... args) {
                        msg = String.format(msg, args);
                        log(msg);
                    }

                };
                objectMap.put("logger", logger);
                objectMap.put("log", logger);

                try {
                    scriptCompile = new ScriptCompile(code, objectMap);
                } catch (Exception e) {
                    onException(bid, e, "初始化JS脚本时报错");
                }
                BID2ENGINE.put(bid, scriptCompile);
                return scriptCompile;
            }
        }
    }

    public static void offerLogMsg(String key, String msg) {
        if (!PRINT_MAP.containsKey(key)) {
            PRINT_MAP.put(key, new LinkedList<>());
        }
        PRINT_MAP.get(key).add("[" + SF_0.format(new Date()) + "] " + msg);
        if (PRINT_MAP.get(key).size() > MAX_LINE)
            PRINT_MAP.get(key).remove(0);
    }

    public static void offerLogMsg0(String key, String msg) {
        offerLogMsg(key, "[系统]" + msg);
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

    @Scheduled(cron = "0 1 */3 * * ?")
    public void deleteMsg() {
        for (JdbcTemplate value : BaseScriptUtils.templateMap.values()) {
            value.execute("VACUUM;");
        }
    }

    @EventHandler
    public void onMessage(@NotNull MessageEvent event) {
        if (event instanceof MessagePreSendEvent) return;
        if (event instanceof MessagePostSendEvent) return;
        String tid = event instanceof GroupMessageEvent ? "g" + event.getSubject().getId() : event instanceof FriendMessageEvent ? "f" + event.getSender().getId() : null;
        if (tid != null) if (configService.isNotOpenK0(event.getBot().getId(), tid)) return;
        ScriptCompile scriptCompile = getJsEngine(event.getBot().getId());
        if (scriptCompile != null) Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                if (isDefined(event.getBot().getId(), scriptCompile, ON_MSG_EVENT_FUNCTION)) {
                    scriptCompile.executeFuc(ON_MSG_EVENT_FUNCTION, toMsg(event.getMessage()), event);
                }
            } catch (Throwable e) {
                onException(event.getBot(), e);
            }
        });
    }

    private static @Nullable Contact getContact(BotEvent event) {
        try {
            Contact contact = null;
            if (event instanceof NudgeEvent) {
                NudgeEvent nudgeEvent = (NudgeEvent) event;
                contact = nudgeEvent.getSubject();
            } else if (event instanceof SignEvent) {
                UserOrBot userOrBot = ((SignEvent) event).getUser();
                if (userOrBot instanceof Member) {
                    contact = ((Member) userOrBot).getGroup();
                } else if (userOrBot instanceof Friend) {
                    contact = ((Friend) userOrBot);
                }
            } else if (event instanceof GroupEvent) {
                contact = ((GroupEvent) event).getGroup();
            } else if (contact instanceof FriendEvent) {
                contact = ((FriendEvent) event).getFriend();
            }
            return contact;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @EventHandler
    public void onEvent(BotEvent event) {
        if (event instanceof MessageEvent) return;
        if (event instanceof MessagePreSendEvent) return;
        if (event instanceof MessagePostSendEvent) return;
        if (event instanceof BotOnlineEvent) return;
        if (event instanceof BotOfflineEvent) return;

        Contact contact = getContact(event);
        if (contact != null) {
            String tid = contact instanceof Friend ? "f" + contact.getId() : "g" + contact.getId();
            if (tid != null) if (configService.isNotOpenK0(event.getBot().getId(), tid)) return;
        }
        ScriptCompile scriptCompile = getJsEngine(event.getBot().getId());
        if (scriptCompile != null) Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                if (isDefined(event.getBot().getId(), scriptCompile, ON_BOT_EVENT_FUNCTION)) {
                    scriptCompile.executeFuc(ON_BOT_EVENT_FUNCTION, event);
                }
            } catch (Throwable e) {
                onException(event.getBot(), e);
            }
        });
    }

    public void onEvent(long bid, String funName, Object eve) {
        ScriptCompile scriptCompile = getJsEngine(bid);
        if (scriptCompile != null) Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                if (isDefined(bid, scriptCompile, funName)) {
                    scriptCompile.executeFuc(funName, eve);
                }
            } catch (Throwable e) {
                onException(bid, e);
            }
        });
    }

    @EventListener
    public void onEvent(ProfileLikeEvent event) {
        long bid = event.getSelfId();
        onEvent(bid, ON_PROFILE_LIKE_FUNCTION, event);
    }

    @EventListener
    public void onEvent(SendLikedEvent event) {
        long bid = event.getSelfId();
        onEvent(bid, ON_SEND_LIKED_FUNCTION, event);
    }

    @EventListener
    public void onEvent(GroupSignEvent event) {
        long bid = event.getSelfId();
        onEvent(bid, ON_GROUP_SIGN_FUNCTION, event);
    }
}
