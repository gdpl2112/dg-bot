package io.github.gdpl2112.dg_bot.service.script.impl;

import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.mapper.SaveMapper;
import io.github.gdpl2112.dg_bot.service.script.ScriptContext;
import io.github.kloping.url.UrlUtils;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.event.events.BotEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.ForwardMessageBuilder;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.MessageChain;

import java.io.ByteArrayInputStream;

import static io.github.gdpl2112.dg_bot.built.ScriptService.getSingleMessages;

public class BasebBotEventScriptContext implements ScriptContext {
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
