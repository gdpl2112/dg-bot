package io.github.gdpl2112.dg_bot.built;

import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.service.script.ScriptContext;
import io.github.kloping.common.Public;
import io.github.kloping.judge.Judge;
import io.github.kloping.map.MapUtils;
import io.github.kloping.url.UrlUtils;
import kotlin.coroutines.CoroutineContext;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageSyncEvent;
import net.mamoe.mirai.event.events.MessageEvent;
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

    @EventHandler
    public void onMessage(@NotNull FriendMessageEvent event) {
        stepScript(event.getBot(), new BaseScriptContext(event, template), event.getMessage());
    }

    @EventHandler
    public void onMessage(@NotNull GroupMessageEvent event) {
        stepScript(event.getBot(), new BaseScriptContext(event, template), event.getMessage());
    }

    @EventHandler
    public void onMessage(@NotNull GroupMessageSyncEvent event) {
        stepScript(event.getBot(), new BaseScriptContext(event, template), event.getMessage());
    }

    private void stepScript(Bot bot, BaseScriptContext context, MessageChain chain) {
        Conf conf = confMapper.selectById(bot.getId());
        if (conf == null) return;
        if (Judge.isEmpty(conf.getCode())) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                ScriptEngine javaScript = SCRIPT_ENGINE_MANAGER.getEngineByName("JavaScript");
                javaScript.put("context", context);
                String msg = toMsg(chain);
                javaScript.put("msg", msg);
                javaScript.eval(conf.getCode());
            } catch (Throwable e) {
                e.printStackTrace();
                System.err.println(String.format("%s Bot 脚本 执行失败", bot.getId()));
            }
        });
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
            return Utils.getValueOrDefault(BID_2_VARIABLES, event.getBot().getId(), name, null);
        }

        @Override
        public Object set(String name, Object value) {
            Object ov = Utils.getValueOrDefault(BID_2_VARIABLES, event.getBot().getId(), name, null);
            MapUtils.append(BID_2_VARIABLES, event.getBot().getId(), name, value, HashMap.class);
            return ov;
        }

        @Override
        public Integer clear() {
            int i = 0;
            Map<String, Object> sizeMap = BID_2_VARIABLES.get(event.getBot().getId());
            if (sizeMap != null) {
                i = sizeMap.size();
                sizeMap.clear();
            }
            return i;
        }

        @Override
        public Object del(String name) {
            Map<String, Object> sizeMap = BID_2_VARIABLES.get(event.getBot().getId());
            if (sizeMap != null) {
                Object oa = sizeMap.get(name);
                sizeMap.remove(name);
                return oa;
            }
            return null;
        }
    }
}
