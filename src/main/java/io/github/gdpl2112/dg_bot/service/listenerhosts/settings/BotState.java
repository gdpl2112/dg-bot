package io.github.gdpl2112.dg_bot.service.listenerhosts.settings;

import net.mamoe.mirai.contact.User;

public interface BotState {

    String getName();

    String getWelcomeMessage();

    String handleInput(User user, String input, Context context);

    default String handleBack(User user, Context context) {
        return context.handleBack(user, context);
    }
}
