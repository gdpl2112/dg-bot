package io.github.gdpl2112.dg_bot.built;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.dao.AllMessage;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.events.GroupSignEvent;
import io.github.gdpl2112.dg_bot.events.ProfileLikeEvent;
import io.github.gdpl2112.dg_bot.events.SendLikedEvent;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.SaveMapper;
import io.github.gdpl2112.dg_bot.service.ConfigService;
import io.github.gdpl2112.dg_bot.service.script.BaseMessageScriptContext;
import io.github.gdpl2112.dg_bot.service.script.BaseScriptUtils;
import io.github.gdpl2112.dg_bot.service.script.ScriptContext;
import io.github.kloping.common.Public;
import io.github.kloping.judge.Judge;
import io.github.kloping.url.UrlUtils;
import kotlin.coroutines.CoroutineContext;
import lombok.Getter;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.data.ForwardMessageBuilder;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.MessageChain;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
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
    public static final String[] NOT_PRINTS = {"未开启", "NOT OPEN", "not open", "exit", "end", "stop"};

    @Autowired
    ConfMapper confMapper;

    @Autowired
    RestTemplate template;

    @Autowired
    SaveMapper saveMapper;

    @Autowired
    ConfigService configService;

    public static Map<String, ScriptException> exceptionMap = new HashMap<>();


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
        String tid = event instanceof GroupMessageEvent ? "g" + event.getSubject().getId() : event instanceof FriendMessageEvent ? "f" + event.getSender().getId() : null;
        if (tid != null) if (configService.isNotOpenK0(event.getBot().getId(), tid)) return;
        final String code = getScriptCode(event.getBot().getId());
        if (Judge.isEmpty(code)) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                ScriptEngine javaScript = SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                javaScript.put("context", new BaseMessageScriptContext(event, saveMapper));
                javaScript.put("utils", new BaseScriptUtils(event.getBot().getId(), template));
                String msg = toMsg(event.getMessage());
                javaScript.put("msg", msg);
                javaScript.eval(code);
            } catch (Throwable e) {
                onException(event.getBot(), e);
            }
        });
    }

    private String toMsg(MessageChain chain) {
        String msg = DgSerializer.messageChainSerializeToString(chain);
        return msg;
    }

    @EventHandler
    public void onEvent(BotEvent event) {
        if (event instanceof MessageEvent) return;
        if (event instanceof MessagePreSendEvent) return;
        if (event instanceof MessagePostSendEvent) return;
        if (event instanceof BotOnlineEvent) return;
        if (event instanceof BotOfflineEvent) return;
        final String code = getScriptCode(event.getBot().getId());
        if (Judge.isEmpty(code)) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                ScriptEngine javaScript = SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                javaScript.put("context", new BasebBotEventScriptContext(event, saveMapper));
                javaScript.put("event", event);
                javaScript.put("utils", new BaseScriptUtils(event.getBot().getId(), template));
                javaScript.put("msg", event.toString());
                javaScript.eval(code);
            } catch (Throwable e) {
                onException(event.getBot(), e);
            }
        });
    }

    public static void onException(Bot bot, Throwable e) {
        onException(bot.getId(), e);
    }

    public static void onException(long bid, Throwable e) {
        if (e instanceof javax.script.ScriptException) {
            String e1 = e.getMessage();
            for (String e0 : NOT_PRINTS) {
                if (e1.contains(e0)) return;
            }
        }
        e.printStackTrace();
        String err = Utils.getExceptionLine(e);
        err = e + err;
        ScriptException se = new ScriptException(err, System.currentTimeMillis(), bid);
        exceptionMap.put(String.valueOf(bid), se);
        System.err.println(String.format("%s Bot 脚本 执行失败", bid));
    }

    public static class BasebBotEventScriptContext implements ScriptContext {
        private BotEvent event;
        private SaveMapper saveMapper;

        public BasebBotEventScriptContext(BotEvent userEvent, SaveMapper saveMapper) {
            this.event = userEvent;
            this.saveMapper = saveMapper;
        }

        @Override
        public MessageChain getRaw() {
            return null;
        }

        @Override
        public void send(String str) {
            if (event instanceof MessageEvent) {
                send(deSerialize(str));
            }
        }

        @Override
        public void send(Message message) {
            if (event instanceof MessageEvent) {
                MessageEvent messageEvent = (MessageEvent) event;
                messageEvent.getSubject().sendMessage(message);
            }
        }

        @Override
        public Bot getBot() {
            return event.getBot();
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
        public ForwardMessageBuilder forwardBuilder() {
            return new ForwardMessageBuilder(event.getBot().getAsFriend());
        }

        @Override
        public Message deSerialize(String msg) {
            return DgSerializer.stringDeserializeToMessageChain(msg, event.getBot(), event.getBot().getAsFriend());
        }

        @Override
        public MessageChain getMessageChainById(int id) {
            return getSingleMessages(id, event, saveMapper);
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

    @Nullable
    public static MessageChain getSingleMessages(int id, BotEvent event, SaveMapper saveMapper) {
        QueryWrapper<AllMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("internal_id", id);
        wrapper.eq("bot_id", event.getBot().getId());
        List<AllMessage> msg = saveMapper.selectList(wrapper);
        if (msg != null && !msg.isEmpty()) {
            return MessageChain.deserializeFromJsonString(msg.get(0).getContent());
        }
        return null;
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

    @EventListener
    public void onEvent(ProfileLikeEvent event) {
        final String code = getScriptCode(event.getSelfId());
        if (Judge.isEmpty(code)) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                ScriptEngine javaScript = SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                javaScript.put("subType", "profile_like");
                javaScript.put("event", event);
                javaScript.put("bot", Bot.getInstance(event.getSelfId()));
                javaScript.eval(code);
            } catch (Throwable e) {
                onException(event.getSelfId(), e);
            }
        });
    }

    @EventListener
    public void onEvent(SendLikedEvent  event) {
        final String code = getScriptCode(event.getSelfId());
        if (Judge.isEmpty(code)) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                ScriptEngine javaScript = SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                javaScript.put("subType", "send_liked");
                javaScript.put("event", event);
                javaScript.put("bot", Bot.getInstance(event.getSelfId()));
                javaScript.eval(code);
            } catch (Throwable e) {
                onException(event.getSelfId(), e);
            }
        });
    }

    @EventListener
    public void onEvent(GroupSignEvent event) {
        final String code = getScriptCode(event.getSelfId());
        if (Judge.isEmpty(code)) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                ScriptEngine javaScript = SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                javaScript.put("subType", "group_sign");
                javaScript.put("event", event);
                javaScript.put("bot", Bot.getInstance(event.getSelfId()));
                javaScript.eval(code);
            } catch (Throwable e) {
                onException(event.getSelfId(), e);
            }
        });
    }


}
