package io.github.gdpl2112.dg_bot.service.listenerhosts.settings;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.User;

/**
 *
 * create on 22:49
 *
 * @author github kloping
 * @since 2025/9/22
 */
public class ConfSetState implements BotState {

    private Bot bot;
    private ConfMapper confMapper;

    public ConfSetState(Bot bid, ConfMapper confMapper) {
        this.bot = bid;
        this.confMapper = confMapper;
    }

    @Override
    public String getName() {
        return "配置设置";
    }

    @Override
    public String getWelcomeMessage() {
        Conf conf = confMapper.selectById(bot.getId());
        if (conf == null) {
            conf = new Conf();
            conf.setQid(String.valueOf(bot.getId()));
            confMapper.insert(conf);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("输入序号设置对应项!");
        sb.append("\n0. 退出");
        sb.append("\n1. 回复CD(秒): ").append(conf.getCd0());
        sb.append("\n2. 复数前置: ").append(conf.getRetell());
        sb.append("\n3. 开启回复: ").append(conf.getOpen0());
        sb.append("\n4. 关闭回复: ").append(conf.getClose0());
        sb.append("\n5. 开启调用: ").append(conf.getOpen1());
        sb.append("\n6. 关闭调用: ").append(conf.getClose1());
        sb.append("\n7. 添加词: ").append(conf.getAdd0());
        sb.append("\n8. 取消添加词: ").append(conf.getCancel0());
        sb.append("\n9. 查询词: ").append(conf.getSelect0());
        sb.append("\n10. 删除词: ").append(conf.getDel0());
        sb.append("\n11. 监听发送到: ").append(conf.getRsid());
        sb.append("\n12. 状态查看: ").append(conf.getStatus0());
        return sb.toString();
    }

    private int setState = 0;

    @Override
    public String handleInput(User user, String input, Context context) {
        if (setState > 0) {
            Conf conf = confMapper.selectById(bot.getId());
            if (conf == null) {
                conf = new Conf();
                conf.setQid(String.valueOf(bot.getId()));
                confMapper.insert(conf);
            }

            switch (setState) {
                case 1:
                    try {
                        Integer cd = Integer.parseInt(input);
                        confMapper.update(null,
                                new LambdaUpdateWrapper<Conf>()
                                        .eq(Conf::getQid, bot.getId()).set(Conf::getCd0, cd));
                        setState = 0;
                        return "成功!\n" + getWelcomeMessage();
                    } catch (NumberFormatException e) {
                        return "请输入整数数字!";
                    }
                case 2:
                    confMapper.update(null,
                            new LambdaUpdateWrapper<Conf>()
                                    .eq(Conf::getQid, bot.getId()).set(Conf::getRetell, input));
                    setState = 0;
                    return "成功!\n" + getWelcomeMessage();
                case 3:
                    confMapper.update(null,
                            new LambdaUpdateWrapper<Conf>()
                                    .eq(Conf::getQid, bot.getId()).set(Conf::getOpen0, input));
                    setState = 0;
                    return "成功!\n" + getWelcomeMessage();
                case 4:
                    confMapper.update(null,
                            new LambdaUpdateWrapper<Conf>()
                                    .eq(Conf::getQid, bot.getId()).set(Conf::getClose0, input));
                    setState = 0;
                    return "成功!\n" + getWelcomeMessage();
                case 5:
                    confMapper.update(null,
                            new LambdaUpdateWrapper<Conf>()
                                    .eq(Conf::getQid, bot.getId()).set(Conf::getOpen1, input));
                    setState = 0;
                    return "成功!\n" + getWelcomeMessage();
                case 6:
                    confMapper.update(null,
                            new LambdaUpdateWrapper<Conf>()
                                    .eq(Conf::getQid, bot.getId()).set(Conf::getClose1, input));
                    setState = 0;
                    return "成功!\n" + getWelcomeMessage();
                case 7:
                    confMapper.update(null,
                            new LambdaUpdateWrapper<Conf>()
                                    .eq(Conf::getQid, bot.getId()).set(Conf::getAdd0, input));
                    setState = 0;
                    return "成功!\n" + getWelcomeMessage();
                case 8:
                    confMapper.update(null,
                            new LambdaUpdateWrapper<Conf>()
                                    .eq(Conf::getQid, bot.getId()).set(Conf::getCancel0, input));
                    setState = 0;
                    return "成功!\n" + getWelcomeMessage();
                case 9:
                    confMapper.update(null,
                            new LambdaUpdateWrapper<Conf>()
                                    .eq(Conf::getQid, bot.getId()).set(Conf::getSelect0, input));
                    setState = 0;
                    return "成功!\n" + getWelcomeMessage();
                case 10:
                    confMapper.update(null,
                            new LambdaUpdateWrapper<Conf>()
                                    .eq(Conf::getQid, bot.getId()).set(Conf::getDel0, input));
                    setState = 0;
                    return "成功!\n" + getWelcomeMessage();
                case 11:
                    confMapper.update(null,
                            new LambdaUpdateWrapper<Conf>()
                                    .eq(Conf::getQid, bot.getId()).set(Conf::getRsid, input));
                    setState = 0;
                    return "成功!\n" + getWelcomeMessage();
                case 12:
                    confMapper.update(null,
                            new LambdaUpdateWrapper<Conf>()
                                    .eq(Conf::getQid, bot.getId()).set(Conf::getStatus0, input));
                    setState = 0;
                    return "成功!\n" + getWelcomeMessage();
                default:
                    setState = 0;
                    return "无效输入!\n" + getWelcomeMessage();
            }
        } else {
            try {
                int i = Integer.parseInt(input);
                if (i >= 1 && i <= 12) {
                    setState = i;
                    switch (i) {
                        case 1:
                            return "请输入需要设置CD秒数";
                        case 2:
                            return "请输入复述前置词";
                        case 3:
                            return "请输入开启回复词";
                        case 4:
                            return "请输入关闭回复词";
                        case 5:
                            return "请输入开启调用词";
                        case 6:
                            return "请输入关闭调用词";
                        case 7:
                            return "请输入添加词";
                        case 8:
                            return "请输入取消添加词";
                        case 9:
                            return "请输入查询词";
                        case 10:
                            return "请输入删除词";
                        case 11:
                            return "请输入监听发送到的ID";
                        case 12:
                            return "请输入状态查看词";
                        default:
                            return "无效选项";
                    }
                } else {
                    return "无效输入! 请输入0-12之间的数字\n" + getWelcomeMessage();
                }
            } catch (NumberFormatException e) {
                return "无效输入! 请输入数字\n" + getWelcomeMessage();
            }
        }
    }

}
