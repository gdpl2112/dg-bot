package io.github.gdpl2112.dg_bot.service.listenerhosts.settings;

import io.github.gdpl2112.dg_bot.mapper.AdministratorMapper;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.GroupConfMapper;
import io.github.gdpl2112.dg_bot.service.CronService;
import io.github.gdpl2112.dg_bot.service.listenerhosts.OptionalService;
import io.github.gdpl2112.dg_bot.service.v11s.V11AutoLikeService;
import lombok.Setter;
import net.mamoe.mirai.contact.User;

@Setter
public class MainMenuState implements BotState {
    @Override
    public String getName() {
        return "主菜单";
    }

    @Override
    public String getWelcomeMessage() {
        return "进入设置模式请勿发送其他无关内容"
                + "\n请选择序号 设置选项"
                + "\n1. 管理设置"
                + "\n2. 定时设置"
                + "\n3. 配置设置"
                + "\n4. 扩展设置"
                + "\n5. V11设置"
                + "\n0. 退出";
    }

    private AdministratorMapper administratorMapper;
    private GroupConfMapper groupConfMapper;
    private CronService cronService;
    private ConfMapper confMapper;
    private OptionalService optionalService;
    private V11AutoLikeService v11ConfService;

    @Override
    public String handleInput(User user, String input, Context context) {
        switch (input) {
            case "1":
                return context.pushState(new AdminMenuState(administratorMapper, groupConfMapper));
            case "2":
                return context.pushState(new CronSetState(cronService, context.getBot()));
            case "3":
                return context.pushState(new ConfSetState(context.getBot(), confMapper));
            case "4":
                return context.pushState(new OptSetState(context.getBot(), optionalService));
            case "5":
                return context.pushState(new V11SetState(context.getBot(), v11ConfService));
            default:
                return "无效输入";
        }
    }
}