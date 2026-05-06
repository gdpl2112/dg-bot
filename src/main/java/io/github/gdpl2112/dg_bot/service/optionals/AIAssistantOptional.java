package io.github.gdpl2112.dg_bot.service.optionals;

import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.dao.AiConf;
import io.github.gdpl2112.dg_bot.mapper.AiConfMapper;
import io.github.gdpl2112.dg_bot.service.listenerhosts.DefaultService;
import io.github.kloping.judge.Judge;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.FriendMessageSyncEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageSyncEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.MessageChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AI助手内置功能（作为可选功能存在）
 * 提供号主与AI大模型的对话交互能力
 */
@Slf4j
@Component
public class AIAssistantOptional implements BaseOptional {

    @Autowired
    private DefaultService defaultService;
    
    @Autowired
    private AiConfMapper aiConfMapper;

    /**
     * 获取可选功能的描述信息
     * @return 描述信息
     */
    @Override
    public String getDesc() {
        return "号主与AI大模型对话功能，号主可直接通过指令与AI对话";
    }

    /**
     * 获取可选功能的名称
     * @return 名称
     */
    @Override
    public String getName() {
        return "AI助手";
    }

    /**
     * 执行拦截和消息处理逻辑
     * @param event 消息事件（可能为群消息或好友消息），非空
     */
    @Override
    public void run(MessageEvent event) {
        Long bid = event.getBot().getId();
        Long sid = event.getSender().getId();

        // 校验发送者是否为具有最高权限的号主
        if (!defaultService.isAdmin(bid, sid)) {
            return;
        }

        // 解析包含特殊字符(如at、表情等)的富文本消息为纯文本格式
        String content = DgSerializer.messageChainSerializeToString(event.getMessage());
        if (Judge.isEmpty(content)) {
            content = MessageChain.serializeToJsonString(event.getMessage());
        }

        if (content == null || content.trim().isEmpty()) {
            return;
        }

        // 提取并获取AI全局配置
        AiConf aiConf = aiConfMapper.selectById(String.valueOf(bid));
        if (aiConf == null || !aiConf.getOpen()) {
            return;
        }

        String aiPrefix = aiConf.getPrefix() != null ? aiConf.getPrefix() : "AI";

        // 提取和判断是否属于AI助手指令前缀，如果是则进行处理
        if (!content.startsWith(aiPrefix)) {
            return;
        }

        // 获取去除前缀后的实际对话内容
        String actualContent = content.substring(aiPrefix.length()).trim();
        if (actualContent.isEmpty()) {
            return;
        }

        // TODO 等我实现 号主与AI对话的具体业务逻辑和API对接
    }
}
