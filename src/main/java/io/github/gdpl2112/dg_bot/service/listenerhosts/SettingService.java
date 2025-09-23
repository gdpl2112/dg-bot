package io.github.gdpl2112.dg_bot.service.listenerhosts;

import io.github.gdpl2112.dg_bot.mapper.AdministratorMapper;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.GroupConfMapper;
import io.github.gdpl2112.dg_bot.mapper.PassiveMapper;
import io.github.gdpl2112.dg_bot.service.CronService;
import io.github.gdpl2112.dg_bot.service.listenerhosts.settings.Context;
import io.github.gdpl2112.dg_bot.service.listenerhosts.settings.MainMenuState;
import io.github.gdpl2112.dg_bot.service.v11s.V11AutoLikeService;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.MessageEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * create on 20:03
 *
 * @author github kloping
 * @since 2025/9/21
 */
@Service
@Slf4j
public class SettingService implements ListenerHost {

    private static Map<Long, Context> BID2CONTEXT = new HashMap<>();

    @Autowired
    DefaultService defaultService;

    public static final String PREFIX = "[设置模式输出]\n";

    @Autowired
    AdministratorMapper administratorMapper;
    @Autowired
    private GroupConfMapper groupConfMapper;
    @Autowired
    private PassiveMapper passiveMapper;
    @Autowired
    private CronService cronService;
    @Autowired
    private ConfMapper confMapper;
    @Autowired
    private OptionalService optionalService;
    @Autowired
    private V11AutoLikeService v11AutoLikeService;

//    @EventHandler
//    public void onEvent(net.mamoe.mirai.event.events.GroupMessageEvent event) {
//        doEvent(event);
//    }

    @EventHandler
    public void onEvent(net.mamoe.mirai.event.events.GroupMessageSyncEvent event) {
        doEvent(event);
    }

    private void doEvent(MessageEvent event) {
        User user = event.getSender();
        String command = event.getMessage().contentToString();
        if (command == null || command.trim().isEmpty()) return;
        command = command.trim();
        if (command.startsWith(PREFIX)) return;
        boolean isAdmin = defaultService.isAdmin(event.getBot().getId(), user.getId());
        if (!isAdmin && user.getId() != event.getBot().getId()) return;
        if (BID2CONTEXT.containsKey(event.getBot().getId())) {
            Context context = BID2CONTEXT.get(event.getBot().getId());
            String handleInput = null;
            try {
                handleInput = context.handleInput(user, command);
                event.getSubject().sendMessage(PREFIX + handleInput);
            } catch (Exception e) {
                e.printStackTrace();
                event.getSubject().sendMessage(PREFIX + "发生错误:" + e.getMessage());
            }
        } else if (command.startsWith("开始设置")) {
            MainMenuState state = new MainMenuState();
            state.setAdministratorMapper(administratorMapper);
            state.setGroupConfMapper(groupConfMapper);
            state.setCronService(cronService);
            state.setConfMapper(confMapper);
            state.setOptionalService(optionalService);
            state.setV11ConfService(v11AutoLikeService);
            Context context = new Context(event.getBot(), state) {
                @Override
                public void close() {
                    BID2CONTEXT.remove(event.getBot().getId());
                }
            };
            BID2CONTEXT.put(event.getBot().getId(), context);
            event.getSubject().sendMessage(PREFIX + context.getCurrentState().getWelcomeMessage());
        }
    }
}
