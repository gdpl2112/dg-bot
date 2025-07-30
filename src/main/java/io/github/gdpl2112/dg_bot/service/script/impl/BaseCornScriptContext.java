package io.github.gdpl2112.dg_bot.service.script.impl;

import io.github.gdpl2112.dg_bot.service.script.ScriptContext;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.message.data.ForwardMessageBuilder;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.MessageChain;

/**
 * @author github kloping
 * @date 2025/7/30-12:41
 */
public class BaseCornScriptContext implements ScriptContext {
    private Bot bot;

    public BaseCornScriptContext(Bot bot) {
        this.bot = bot;
    }

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
}
