package io.github.gdpl2112.dg_bot.service.listenerhosts.settings;

import lombok.Getter;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.User;

import java.util.Deque;
import java.util.LinkedList;

// 2. 上下文类，现在维护一个状态栈
public abstract class Context {
    private Deque<BotState> stateStack = new LinkedList<>(); // 使用栈记录状态历史
    @Getter
    private Bot bot;

    public Context(Bot bot, BotState initialState) {
        this.bot = bot;
        pushState(initialState);
    }

    public Context(BotState initialState) {
        stateStack.push(initialState);
    }

    public BotState getCurrentState() {
        return stateStack.peek();
    }

    public String pushState(BotState newState) {
        stateStack.push(newState);
        return newState.getWelcomeMessage();
    }

    public BotState popState() {
        return stateStack.pop();
    }

    public String getCurrentStateName() {
        String end = "";
        for (BotState botState : stateStack) {
            end = botState.getName() + ">" + end;
        }
        return end.isEmpty() ? "" : end.substring(0, end.length() - 1);
    }

    private static final String PATH_PREFIX = "[当前路径]";

    public String handleInput(User user, String input) {
        if ("退出".equals(input) || "0".equals(input)) {
            String out = getCurrentState().handleBack(user, this);
            return PATH_PREFIX + getCurrentStateName() + "\n" + out;
        } else {
            String out = getCurrentState().handleInput(user, input, this);
            return PATH_PREFIX + getCurrentStateName() + "\n" + out;
        }
    }

    public String handleBack(User user, Context context) {
        BotState preBotState = popState();
        BotState botState = getCurrentState();
        if (botState == null) {
            close();
            return "已退出设置模式!";
        } else return botState.getWelcomeMessage();
    }

    public abstract void close();
}