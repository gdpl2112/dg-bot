package io.github.gdpl2112.dg_bot.service.listenerhosts.settings;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.gdpl2112.dg_bot.dao.AiConf;
import io.github.gdpl2112.dg_bot.mapper.AiConfMapper;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.User;

/**
 * AI助手配置设置状态
 */
public class AiSetState implements BotState {

    private Bot bot;
    private AiConfMapper aiConfMapper;
    private int setState = 0;

    public AiSetState(Bot bot, AiConfMapper aiConfMapper) {
        this.bot = bot;
        this.aiConfMapper = aiConfMapper;
    }

    @Override
    public String getName() {
        return "AI设置";
    }

    @Override
    public String getWelcomeMessage() {
        AiConf conf = aiConfMapper.selectById(bot.getId());
        if (conf == null) {
            conf = new AiConf();
            conf.setQid(String.valueOf(bot.getId()));
            aiConfMapper.insert(conf);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("输入序号设置对应项!");
        sb.append("\n0. 退出");
        sb.append("\n1. 开启AI助手: ").append(conf.getOpen() ? "是✅" : "否❌");
        sb.append("\n2. 唤醒前缀: ").append(conf.getPrefix());
        sb.append("\n3. API Key: ").append(conf.getApiKey().isEmpty() ? "未设置" : "已设置(隐)");
        sb.append("\n4. Base URL: ").append(conf.getBaseUrl());
        sb.append("\n5. 模型ID: ").append(conf.getModelId());
        sb.append("\n6. 随机性(Temp): ").append(conf.getTemperature());
        sb.append("\n7. 允许联网: ").append(conf.getNetwork() ? "是✅" : "否❌");
        return sb.toString();
    }

    @Override
    public String handleInput(User user, String input, Context context) {
        if (setState > 0) {
            AiConf conf = aiConfMapper.selectById(bot.getId());
            if (conf == null) {
                conf = new AiConf();
                conf.setQid(String.valueOf(bot.getId()));
                aiConfMapper.insert(conf);
            }

            switch (setState) {
                case 2:
                    aiConfMapper.update(null,
                            new LambdaUpdateWrapper<AiConf>()
                                    .eq(AiConf::getQid, bot.getId()).set(AiConf::getPrefix, input));
                    setState = 0;
                    return "设置成功!\n" + getWelcomeMessage();
                case 3:
                    aiConfMapper.update(null,
                            new LambdaUpdateWrapper<AiConf>()
                                    .eq(AiConf::getQid, bot.getId()).set(AiConf::getApiKey, input));
                    setState = 0;
                    return "设置成功!\n" + getWelcomeMessage();
                case 4:
                    aiConfMapper.update(null,
                            new LambdaUpdateWrapper<AiConf>()
                                    .eq(AiConf::getQid, bot.getId()).set(AiConf::getBaseUrl, input));
                    setState = 0;
                    return "设置成功!\n" + getWelcomeMessage();
                case 5:
                    aiConfMapper.update(null,
                            new LambdaUpdateWrapper<AiConf>()
                                    .eq(AiConf::getQid, bot.getId()).set(AiConf::getModelId, input));
                    setState = 0;
                    return "设置成功!\n" + getWelcomeMessage();
                case 6:
                    try {
                        Double temp = Double.parseDouble(input);
                        aiConfMapper.update(null,
                                new LambdaUpdateWrapper<AiConf>()
                                        .eq(AiConf::getQid, bot.getId()).set(AiConf::getTemperature, temp));
                        setState = 0;
                        return "设置成功!\n" + getWelcomeMessage();
                    } catch (NumberFormatException e) {
                        return "请输入有效的数字!";
                    }
                default:
                    setState = 0;
                    return "无效输入!\n" + getWelcomeMessage();
            }
        } else {
            try {
                int i = Integer.parseInt(input);
                if (i >= 1 && i <= 7) {
                    AiConf conf = aiConfMapper.selectById(bot.getId());
                    if (conf == null) {
                        conf = new AiConf();
                        conf.setQid(String.valueOf(bot.getId()));
                        aiConfMapper.insert(conf);
                    }
                    
                    switch (i) {
                        case 1:
                            aiConfMapper.update(null,
                                    new LambdaUpdateWrapper<AiConf>()
                                            .eq(AiConf::getQid, bot.getId()).set(AiConf::getOpen, !conf.getOpen()));
                            return getWelcomeMessage();
                        case 2:
                            setState = i;
                            return "请输入AI唤醒前缀(例如: AI 或 聊天)";
                        case 3:
                            setState = i;
                            return "请输入大模型 API Key";
                        case 4:
                            setState = i;
                            return "请输入 API Base URL(例如: https://api.openai.com/v1)";
                        case 5:
                            setState = i;
                            return "请输入模型ID(例如: gpt-3.5-turbo)";
                        case 6:
                            setState = i;
                            return "请输入Temperature参数(例如: 0.7)";
                        case 7:
                            aiConfMapper.update(null,
                                    new LambdaUpdateWrapper<AiConf>()
                                            .eq(AiConf::getQid, bot.getId()).set(AiConf::getNetwork, !conf.getNetwork()));
                            return getWelcomeMessage();
                        default:
                            return "无效选项";
                    }
                } else {
                    return "无效输入! 请输入0-7之间的数字\n" + getWelcomeMessage();
                }
            } catch (NumberFormatException e) {
                return "无效输入!\n" + getWelcomeMessage();
            }
        }
    }
}
