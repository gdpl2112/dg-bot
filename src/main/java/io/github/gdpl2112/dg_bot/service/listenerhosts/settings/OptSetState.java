package io.github.gdpl2112.dg_bot.service.listenerhosts.settings;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.gdpl2112.dg_bot.dao.Optional;
import io.github.gdpl2112.dg_bot.dto.OptionalDto;
import io.github.gdpl2112.dg_bot.service.listenerhosts.OptionalService;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.User;

import java.util.List;

/**
 *
 * create on 23:17
 *
 * @author github kloping
 * @since 2025/9/22
 */
public class OptSetState implements BotState {
    private Bot bot;
    private OptionalService optionalService;

    public OptSetState(Bot bot, OptionalService optionalService) {
        this.bot = bot;
        this.optionalService = optionalService;
    }

    @Override
    public String getName() {
        return "扩展设置";
    }

    private List<OptionalDto> dtos = null;

    @Override
    public String getWelcomeMessage() {
        dtos = optionalService.getOptionalDtos(String.valueOf(bot.getId()));
        StringBuilder sb = new StringBuilder("选项序号以切换对应开关!");
        sb.append("\n 0. 退出");
        for (int i = 0; i < dtos.size(); i++) {
            OptionalDto dto = dtos.get(i);
            sb.append("\n ").append(i + 1).append(". ")
                    .append(dto.getName()).append(": ").append(dto.getOpen() ? "开✅" : "关❌");
            if (dto.getDesc() != null)
                sb.append("\n描述:").append(dto.getDesc());
            sb.append("\n------------");
        }
        return sb.toString();
    }

    @Override
    public String handleInput(User user, String input, Context context) {
        try {
            Integer i = Integer.parseInt(input);
            if (dtos != null && i > 0 && i <= dtos.size()) {
                OptionalDto dto = dtos.get(i - 1);
                optionalService.optionalMapper.update(null,
                        new LambdaUpdateWrapper<Optional>()
                                .eq(Optional::getQid, bot.getId())
                                .eq(Optional::getOpt, dto.getOpt())
                                .set(Optional::getOpen, !dto.getOpen()));
                return "成功!\n" + getWelcomeMessage();
            }
        } catch (NumberFormatException e) {

        }
        return "无效输入!";
    }
}
