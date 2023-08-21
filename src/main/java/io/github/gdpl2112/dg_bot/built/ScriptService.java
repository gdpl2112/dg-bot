package io.github.gdpl2112.dg_bot.built;

import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.service.script.ScriptContext;
import io.github.gdpl2112.dg_bot.service.script.ScriptUtils;
import io.github.kloping.common.Public;
import io.github.kloping.judge.Judge;
import io.github.kloping.map.MapUtils;
import io.github.kloping.url.UrlUtils;
import kotlin.coroutines.CoroutineContext;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.event.Event;
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
import java.util.*;

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

    @EventHandler
    public void onMessage(@NotNull FriendMessageEvent event) {
        stepMsgScript(event.getBot(), new BaseScriptUtils(event.getBot().getId(), template),
                new BaseScriptContext(event, template), event.getMessage());
    }

    @EventHandler
    public void onMessage(@NotNull GroupMessageEvent event) {
        stepMsgScript(event.getBot(), new BaseScriptUtils(event.getBot().getId(), template),
                new BaseScriptContext(event, template), event.getMessage());
    }

    @EventHandler
    public void onMessage(@NotNull GroupMessageSyncEvent event) {
        stepMsgScript(event.getBot(), new BaseScriptUtils(event.getBot().getId(), template),
                new BaseScriptContext(event, template), event.getMessage());
    }

    private void stepMsgScript(Bot bot, BaseScriptUtils utils, BaseScriptContext context, MessageChain chain) {
        final String code = getScriptCode(bot.getId());
        if (code == null) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                ScriptEngine javaScript = SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                javaScript.put("context", context);
                javaScript.put("utils", utils);
                String msg = toMsg(chain);
                javaScript.put("msg", msg);
                javaScript.eval(code);
            } catch (Throwable e) {
                onException(bot, e);
            }
        });
    }

    private String getScriptCode(long bid) {
        Conf conf = confMapper.selectById(bid);
        if (conf == null) return null;
        if (Judge.isEmpty(conf.getCode())) return null;
        return conf.getCode();
    }

    private void stepEventScript(Bot bot, BaseScriptUtils utils, Event event, String type) {
        final String code = getScriptCode(bot.getId());
        if (code == null) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                ScriptEngine javaScript = SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                javaScript.put("context", new Object() {
                    public String getType() {
                        return type;
                    }
                });
                javaScript.put("event", event);
                javaScript.put("utils", utils);
                javaScript.put("msg", event.toString());
                javaScript.eval(code);
            } catch (Throwable e) {
                onException(bot, e);
            }
        });
    }

    @EventHandler
    public void onEvent(GroupMemberEvent event) {
        stepEventScript(event.getBot(), new BaseScriptUtils(event.getBot().getId(), template),
                event, event.getClass().getSimpleName());
    }

    @EventHandler
    public void onEvent(FriendEvent event) {
        stepEventScript(event.getBot(), new BaseScriptUtils(event.getBot().getId(), template),
                event, event.getClass().getSimpleName());
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

    public static class BaseScriptContext implements ScriptContext {
        private MessageEvent event;
        private RestTemplate template;

        public BaseScriptContext(MessageEvent event, RestTemplate template) {
            this.event = event;
            this.template = template;
        }

        @Override
        public Bot getBot() {
            return event.getBot();
        }

        @Override
        public void send(String str) {
            event.getSubject().sendMessage(MiraiCode.deserializeMiraiCode(str));
        }

        @Override
        public void send(Message message) {
            event.getSubject().sendMessage(message);
        }

        @Override
        public MessageChainBuilder builder() {
            return new MessageChainBuilder();
        }

        @Override
        public ForwardMessageBuilder forwardBuilder() {
            return new ForwardMessageBuilder(getSubject());
        }

        @Override
        public MusicShare createMusicShare(String kind, String title, String summer, String jumUrl, String picUrl, String url) {
            return new MusicShare(MusicKind.valueOf(kind), title, summer, jumUrl, picUrl, url);
        }

        @Override
        public User getSender() {
            return event.getSender();
        }

        @Override
        public Contact getSubject() {
            return event.getSubject();
        }

        @Override
        public Image uploadImage(String url) {
            try {
                byte[] bytes = UrlUtils.getBytesFromHttpUrl(url);
                Image image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(bytes));
                return image;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public PlainText newPlainText(String text) {
            return new PlainText(text);
        }

        @Override
        public String getType() {
            return event instanceof GroupMessageEvent || event instanceof GroupMessageSyncEvent ? "group" : event instanceof FriendMessageEvent ? "friend" : "Unknown";
        }
    }

    public static class BaseScriptUtils implements ScriptUtils {
        private long bid;
        private RestTemplate template;

        public BaseScriptUtils(Long bid, RestTemplate template) {
            this.template = template;
            this.bid = bid;
        }

        @Override
        public String requestGet(String url) {
            return template.getForObject(url, String.class);
        }

        @Override
        public String requestPost(String url, String data) {
            return template.postForObject(url, data, String.class);
        }

        @Override
        public Object get(String name) {
            return Utils.getValueOrDefault(BID_2_VARIABLES, bid, name, null);
        }

        @Override
        public Object set(String name, Object value) {
            Object ov = Utils.getValueOrDefault(BID_2_VARIABLES, bid, name, null);
            MapUtils.append(BID_2_VARIABLES, bid, name, value, HashMap.class);
            return ov;
        }

        @Override
        public Integer clear() {
            int i = 0;
            Map<String, Object> sizeMap = BID_2_VARIABLES.get(bid);
            if (sizeMap != null) {
                i = sizeMap.size();
                sizeMap.clear();
            }
            return i;
        }

        @Override
        public Object del(String name) {
            Map<String, Object> sizeMap = BID_2_VARIABLES.get(bid);
            if (sizeMap != null) {
                Object oa = sizeMap.get(name);
                sizeMap.remove(name);
                return oa;
            }
            return null;
        }

        @Override
        public List<Map.Entry<String, Object>> list() {
            if (BID_2_VARIABLES.containsKey(bid)) return new LinkedList<>(BID_2_VARIABLES.get(bid).entrySet());
            return new ArrayList<>();
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
