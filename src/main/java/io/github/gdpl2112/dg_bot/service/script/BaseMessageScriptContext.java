package io.github.gdpl2112.dg_bot.service.script;

import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.kloping.url.UrlUtils;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageSyncEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.code.MiraiCode;
import net.mamoe.mirai.message.data.ForwardMessageBuilder;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.Message;

import java.io.ByteArrayInputStream;

public class BaseMessageScriptContext implements ScriptContext {
    private MessageEvent event;

    public BaseMessageScriptContext(MessageEvent event) {
        this.event = event;
    }

    @Override
    public Bot getBot() {
        return event.getBot();
    }

    @Override
    public void send(String str) {
        event.getSubject().sendMessage(DgSerializer.stringDeserializeToMessageChain(str,event.getBot(),event.getSubject()));
    }

    @Override
    public void send(Message message) {
        event.getSubject().sendMessage(message);
    }

    @Override
    public ForwardMessageBuilder forwardBuilder() {
        return new ForwardMessageBuilder(getSubject());
    }

    @Override
    public Message deSerialize(String msg) {
        return DgSerializer.stringDeserializeToMessageChain(msg, event.getBot(), event.getSubject());
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
            Image image = Contact.uploadImage(event.getBot().getAsFriend(), new ByteArrayInputStream(bytes));
            return image;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String getType() {
        return event instanceof GroupMessageEvent || event instanceof GroupMessageSyncEvent ?
                "group" : event instanceof FriendMessageEvent ? "friend" : "Unknown";
    }
}
