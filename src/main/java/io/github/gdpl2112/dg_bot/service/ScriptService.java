package io.github.gdpl2112.dg_bot.service;

import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
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
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
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

/**
 * @author github.kloping
 */
@Service
public class ScriptService extends SimpleListenerHost {
    @Override
    public void handleException(@NotNull CoroutineContext context, @NotNull Throwable exception) {
        exception.printStackTrace();
    }

    private final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

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

    private void stepScript(Bot bot, BaseScriptContext context, MessageChain chain) {
        Conf conf = confMapper.selectById(bot.getId());
        if (conf == null) return;
        if (Judge.isEmpty(conf.getCode())) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                ScriptEngine javaScript = scriptEngineManager.getEngineByName("JavaScript");
                javaScript.put("context", context);
                String msg = toMsg(chain);
                javaScript.put("msg", msg);
                javaScript.eval(conf.getCode());
            } catch (Exception e) {
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

    public static class BaseScriptContext implements ScriptContext {
        private MessageEvent event;
        private RestTemplate template;

        public BaseScriptContext(MessageEvent event, RestTemplate template) {
            this.event = event;
            this.template = template;
        }

        @Override
        public void send(String str) {
            event.getSubject().sendMessage(str);
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
        public Image uploadImage(String url) {
            byte[] bytes = UrlUtils.getBytesFromHttpUrl(url);
            Image image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(bytes));
            return image;
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
        public String get(String url) {
            return template.getForObject(url, String.class);
        }

        @Override
        public String post(String url, String data) {
            return template.postForObject(url, data, String.class);
        }

        @Override
        public String getType() {
            return event instanceof GroupMessageEvent ? "group" : event instanceof FriendMessageEvent ? "friend" : "Unknown";
        }
    }
}
