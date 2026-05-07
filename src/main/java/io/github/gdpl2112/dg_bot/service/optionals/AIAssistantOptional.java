package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.dao.AiConf;
import io.github.gdpl2112.dg_bot.dao.CronMessage;
import io.github.gdpl2112.dg_bot.dto.VoteResponseDTO;
import io.github.gdpl2112.dg_bot.mapper.AiConfMapper;
import io.github.gdpl2112.dg_bot.mapper.CronMapper;
import io.github.gdpl2112.dg_bot.service.CronService;
import io.github.gdpl2112.dg_bot.service.listenerhosts.DefaultService;
import io.github.kloping.judge.Judge;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.events.GroupAwareMessageEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.PlainText;
import net.mamoe.mirai.message.data.SingleMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.kloping.core.ai.McpBean;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI助手内置功能（作为可选功能存在）
 * 提供号主与AI大模型的对话交互能力
 */
@Slf4j
@Component
public class AIAssistantOptional implements BaseOptional {

    private static final Map<Long, Image> BID_LAST_IMAGE_MAP = new ConcurrentHashMap<>();

    private static final Map<String, ChatClient> CHAT_CLIENT_CACHE = new ConcurrentHashMap<>();

    /**
     * ChatClient实例缓存，key为bid(qid)
     */
    private static final Map<String, Deque<MemoryMessage>> MESSAGE_MEMORY_CACHE = new ConcurrentHashMap<>();

    @Autowired
    private DefaultService defaultService;

    @Autowired
    private AiConfMapper aiConfMapper;

    @Autowired
    private CronMapper cronMapper;

    @Autowired
    private CronService cronService;

    @Autowired
    private McpBean mcpBean;

    /**
     * 获取可选功能的描述信息
     *
     * @return 描述信息
     */
    @Override
    public String getDesc() {
        return "号主与AI大模型对话功能，号主可直接通过指令与AI对话";
    }

    /**
     * 获取可选功能的名称
     *
     * @return 名称
     */
    @Override
    public String getName() {
        return "AI助手";
    }

    public static Image getLastImage(Long bid) {
        return BID_LAST_IMAGE_MAP.get(bid);
    }

    @Override
    public void run(MessageEvent event) {
        Long bid = event.getBot().getId();
        Long sid = event.getSender().getId();

        // 校验发送者是否为具有最高权限的号主
        if (bid.longValue() != sid.longValue() && !defaultService.isAdmin(bid, sid)) {
            return;
        }

        // 记录当前账号最后发送的图片，供后续 AI 对话使用
        Image lastImage = null;
        for (SingleMessage singleMessage : event.getMessage()) {
            if (singleMessage instanceof Image) {
                lastImage = (Image) singleMessage;
            }
        }
        if (lastImage != null) {
            BID_LAST_IMAGE_MAP.put(bid, lastImage);
        }

        // 解析包含特殊字符(如at、表情等)的富文本消息为纯文本格式
        String content = DgSerializer.messageChainSerializeToString(event.getMessage());
        if (Judge.isEmpty(content)) {
            content = MessageChain.serializeToJsonString(event.getMessage());
        }

        if (content == null || content.trim().isEmpty()) {
            return;
        } else {
            content = content.trim();
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

        // 提取去除前缀后的实际对话内容
        String actualContent = content.substring(aiPrefix.length()).trim();
        if (actualContent.isEmpty()) {
            return;
        }

        log.info("AI助手对话 bid:{} sid:{} content:{}", bid, sid, actualContent);

        String systemPrompt = String.format(SYSTEM_PROMPT_TEXT, aiConf.getName(), aiConf.getTrait(),
                event instanceof GroupAwareMessageEvent ? "Group" : "Friend", event.getSubject().getId(), event.getBot().getId());
        String memoryKey = buildMemoryKey(bid, event.getSubject().getId());
        appendMemory(memoryKey, "用户", actualContent, resolveMaxMessage(aiConf.getMaxMessage()));
        String memoryPrompt = buildMemoryPrompt(systemPrompt, memoryKey, resolveMaxMessage(aiConf.getMaxMessage()));

        ChatClient chatClient = getChatClient(aiConf);
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = chatClient.prompt()
                .system(memoryPrompt)
                .user(actualContent)
                .toolCallbacks(getToolCallbacks(aiConf, memoryKey));
        if (aiConf.getNetwork()) {
            chatClientRequestSpec.toolCallbacks(mcpBean.getToolCallbacks());
        }

        try {
            String responseContent = chatClientRequestSpec.call().content();
            if (responseContent == null || responseContent.trim().isEmpty()) {
                return;
            }
            String reply = "['" + aiConf.getName() + "'回答]\n\n" + responseContent;
            event.getSubject().sendMessage(new PlainText(reply));
            appendMemory(memoryKey, "助手", responseContent.trim(), resolveMaxMessage(aiConf.getMaxMessage()));
        } catch (Exception e) {
            log.error("AI助手对话调用失败 bid:{} sid:{}", bid, sid, e);
            event.getSubject().sendMessage(new PlainText("AI调用失败，请稍后再试" + "\n\n['" + aiConf.getName() + "'回复]"));
        }
    }

    private static String buildMemoryKey(Long bid, Long subjectId) {
        return bid + ":" + subjectId;
    }

    private static int resolveMaxMessage(Integer maxMessage) {
        return maxMessage == null || maxMessage <= 0 ? 10 : maxMessage;
    }

    private static String buildMemoryPrompt(String systemPrompt, String memoryKey, int maxMessage) {
        Deque<MemoryMessage> memory = MESSAGE_MEMORY_CACHE.get(memoryKey);
        if (memory == null || memory.isEmpty()) {
            return systemPrompt;
        }

        StringBuilder builder = new StringBuilder(systemPrompt).append("\n\n历史对话：\n");
        synchronized (memory) {
            int start = Math.max(0, memory.size() - maxMessage);
            int index = 0;
            for (MemoryMessage memoryMessage : memory) {
                if (index++ < start) {
                    continue;
                }
                builder.append(memoryMessage.role()).append('：').append(memoryMessage.content()).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private static void appendMemory(String memoryKey, String role, String content, int maxMessage) {
        Deque<MemoryMessage> memory = MESSAGE_MEMORY_CACHE.computeIfAbsent(memoryKey, k -> new ArrayDeque<>());
        synchronized (memory) {
            memory.addLast(new MemoryMessage(role, content));
            while (memory.size() > maxMessage) {
                memory.removeFirst();
            }
        }
    }

    private record MemoryMessage(String role, String content) {
    }

    private static final String SYSTEM_PROMPT_TEXT = """
            You are an assistant built into the QQ proxy service. Your name is %s.
            Only answer content related to the question or QQ-related fields. 
            It is strictly prohibited to output any markdown format. Only emoji, emoticons and text are allowed.
            Reply in the same language as the input.
            Style requirement: %s. Current environment: %s, environment ID: %s,bot ID: %s. It is strictly prohibited to cross over to another bid.
            """;

    /**
     * 获取ChatClient实例，优先从缓存获取；缓存不存在则创建并缓存
     *
     * @param aiConf AI配置，非空，qid作为缓存key
     * @return ChatClient实例
     */
    public static ChatClient getChatClient(AiConf aiConf) {
        return CHAT_CLIENT_CACHE.computeIfAbsent(aiConf.getQid(), k -> createChatClient(aiConf));
    }

    /**
     * 清除指定bid的ChatClient缓存，配置更新时调用
     *
     * @param bid BID，非空
     */
    public static void evictChatClient(String bid) {
        CHAT_CLIENT_CACHE.remove(bid);
    }

    /**
     * 根据AI配置创建ChatClient实例
     *
     * @param aiConf AI配置，非空
     * @return 新创建的ChatClient实例
     */
    private static ChatClient createChatClient(AiConf aiConf) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(aiConf.getBaseUrl())
                .apiKey(aiConf.getApiKey())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(aiConf.getModelId())
                .temperature(aiConf.getTemperature())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        return ChatClient.builder(chatModel).build();
    }

    /**
     * 根据最近的N次消息记录 判断 需要用到哪些tool 需要格式化输出
     * <p>
     * 输入 工机具描述和 工具id
     * 输出 工具id
     *
     * @param conf
     * @param memoryKey
     * @return
     */
    private List<ToolCallback> getToolCallbacks(AiConf conf, String memoryKey) {
        initToolCache();
        String toolListData = cachedToolListData;

        // 取最近3条对话记录作为工具选择的上下文
        Deque<MemoryMessage> memory = MESSAGE_MEMORY_CACHE.get(memoryKey);
        StringBuilder recentMessages = new StringBuilder();
        if (memory != null && !memory.isEmpty()) {
            synchronized (memory) {
                int start = Math.max(0, memory.size() - 5);
                int index = 0;
                for (MemoryMessage msg : memory) {
                    if (index++ < start) continue;
                    recentMessages.append(msg.role()).append("：").append(msg.content()).append("\n");
                }
            }
        }

        String systemPrompt = "你是一个工具选择器。根据用户最近的对话记录，从可用工具列表中选出可能需要用到的工具名称。" +
                "只输出一个JSON数组，数组元素为工具name字符串，不需要其他任何文字。如果不需要任何工具则输出空数组[]。\n" +
                "输出示例：[\"set_group_card\",\"send_like\"]\n\n" +
                "可用工具列表(name->description)：\n" + toolListData;

        String userPrompt = recentMessages.isEmpty() ? "暂无对话记录" : recentMessages.toString().trim();

        String result;
        try {
            ChatClient chatClient = getChatClient(conf);
            result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call().content();
        } catch (Exception e) {
            // 工具选择AI调用失败时降级返回全部工具
            log.warn("工具选择AI调用失败，降级返回全部工具", e);
            return new ArrayList<>(cachedToolName2tool.values());
        }

        // 提取JSON数组部分（兼容AI可能附带多余文字的情况）
        if (result == null || result.trim().isEmpty()) {
            return new ArrayList<>(cachedToolName2tool.values());
        }
        int arrStart = result.indexOf('[');
        int arrEnd = result.lastIndexOf(']');
        if (arrStart < 0 || arrEnd < 0 || arrEnd <= arrStart) {
            return new ArrayList<>(cachedToolName2tool.values());
        }

        JSONArray tools = JSON.parseArray(result.substring(arrStart, arrEnd + 1));
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        for (int i = tools.size() - 1; i >= 0; i--) {
            String toolName = tools.getString(i);
            ToolCallback tool = cachedToolName2tool.get(toolName);
            if (tool == null) {
                log.warn("toolName not found: {}. on conf: {}", toolName, conf.getQid());
            } else {
                toolCallbacks.add(tool);
            }
        }
        // 如果AI没选中任何工具则降级返回全部
        return toolCallbacks.isEmpty() ? new ArrayList<>(cachedToolName2tool.values()) : toolCallbacks;
    }


    /**
     * 将 AiAssistantOptional 中所有 @Tool 方法逐一提取为独立的 ToolCallback
     *
     * @return ToolCallback 数组，每个元素对应一个 @Tool 方法
     */
    public ToolCallback[] getToolCallbacks() {
        if (aiAssistantOptional == null) {
            aiAssistantOptional = new AiAssistantOptional(cronMapper, cronService);
        }
        return ToolCallbacks.from(aiAssistantOptional);
    }

    private AiAssistantOptional aiAssistantOptional;

    /** 工具名称->描述 缓存，由 @Tool 方法固定生成，无需每次重建 */
    private Map<String, String> cachedToolName2Desc;
    /** 工具名称->ToolCallback 缓存，由 @Tool 方法固定生成，无需每次重建 */
    private Map<String, ToolCallback> cachedToolName2tool;
    /** 工具列表JSON缓存，由 cachedToolName2Desc 序列化生成 */
    private String cachedToolListData;

    /**
     * 懒加载初始化工具缓存，首次调用时从 @Tool 方法提取并缓存
     */
    private synchronized void initToolCache() {
        if (cachedToolName2Desc != null) return;
        cachedToolName2Desc = new HashMap<>();
        cachedToolName2tool = new HashMap<>();
        for (ToolCallback toolCallback : getToolCallbacks()) {
            cachedToolName2Desc.put(toolCallback.getToolDefinition().name(), toolCallback.getToolDefinition().description());
            cachedToolName2tool.put(toolCallback.getToolDefinition().name(), toolCallback);
        }
        cachedToolListData = JSON.toJSONString(cachedToolName2Desc);
    }

    @AllArgsConstructor
    public static class AiAssistantOptional {
        private final CronMapper cronMapper;
        private final CronService cronService;

        /**
         * 机器人校验结果封装
         * 用于统一返回可用的 RemoteBot 或错误信息
         */
        private record BotResolveResult(RemoteBot remoteBot, String errorMessage) {
            private boolean success() {
                return remoteBot != null;
            }
        }

        /**
         * 校验机器人是否存在、在线且支持远程操作
         *
         * @param bid 机器人ID
         * @return 校验结果，成功时包含 RemoteBot，失败时包含错误信息
         */
        private static BotResolveResult resolveRemoteBot(Long bid) {
            if (bid == null) {
                return new BotResolveResult(null, "BID不能为空");
            }

            Bot bot = Bot.getInstanceOrNull(bid);
            // 机器人不存在时直接返回错误信息
            if (bot == null) {
                return new BotResolveResult(null, "机器人未找到");
            }
            // 机器人离线时不可执行远程操作
            if (!bot.isOnline()) {
                return new BotResolveResult(null, "机器人未在线");
            }
            // 仅支持 RemoteBot 的机器人实例
            if (!(bot instanceof RemoteBot remoteBot)) {
                return new BotResolveResult(null, "当前机器人不支持该操作");
            }
            return new BotResolveResult(remoteBot, null);
        }

        /**
         * set_group_special_title
         * {
         * "group_id": "123456",
         * "user_id": "123456789",
         * "special_title": "头衔"
         * }
         *
         * @param bid
         * @param groupId
         * @param userId
         * @param title
         * @return
         */
        @Tool(description = "设置群头衔")
        public String set_group_special_title(
                @ToolParam(description = "BID") Long bid,
                @ToolParam(description = "GroupID") Long groupId,
                @ToolParam(description = "QQID") Long userId,
                @ToolParam(description = "群头衔内容") String title) {
            if (bid == null || groupId == null || userId == null || title == null) {
                return "参数不能为空";
            }

            BotResolveResult botResult = resolveRemoteBot(bid);
            if (!botResult.success()) {
                return botResult.errorMessage();
            }
            RemoteBot remoteBot = botResult.remoteBot();

            String payload = "{\"group_id\":\"" + groupId + "\",\"user_id\":\"" + userId
                    + "\",\"special_title\":\"" + title.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
            return remoteBot.executeAction("set_group_special_title", payload);
        }

        /**
         * set_group_card
         * {
         * "group_id": "123456",
         * "user_id": "123456789",
         * "card": "新名片"
         * }
         *
         * @param bid
         * @param groupId
         * @param userId
         * @param card
         */
        @Tool(description = "设置群名片")
        public String set_group_card(
                @ToolParam(description = "BID") Long bid,
                @ToolParam(description = "GroupID") Long groupId,
                @ToolParam(description = "QQID") Long userId,
                @ToolParam(description = "群名片内容") String card) {
            BotResolveResult botResult = resolveRemoteBot(bid);
            if (!botResult.success()) {
                return botResult.errorMessage();
            }
            RemoteBot remoteBot = botResult.remoteBot();
            JSONObject payload = new JSONObject();
            payload.put("group_id", groupId);
            payload.put("user_id", userId);
            payload.put("card", card);
            return remoteBot.executeAction("set_group_card", payload.toJSONString());
        }

        // 设置qq头像
        //{
        //  "file": "base64://..."
        //}
        @Tool(description = "设置QQ头像,为最近发的一个图片")
        public String set_qq_avatar(@ToolParam(description = "BID") Long bid) {
            BotResolveResult botResult = resolveRemoteBot(bid);
            if (!botResult.success()) {
                return botResult.errorMessage();
            }
            RemoteBot remoteBot = botResult.remoteBot();

            Image lastImage = AIAssistantOptional.getLastImage(bid);
            if (lastImage == null) {
                return "未找到最近发送的图片";
            }

            String url = net.mamoe.mirai.message.data.Image.queryUrl(lastImage);
            if (url == null || url.isEmpty()) {
                return "获取图片URL失败";
            }

            try {
                // 使用 okhttp 下载图片转为 base64
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
                byte[] bytes;
                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        return "下载图片失败: HTTP " + response.code();
                    }
                    bytes = response.body().bytes();
                }
                String base64 = java.util.Base64.getEncoder().encodeToString(bytes);

                JSONObject payload = new JSONObject();
                payload.put("file", "base64://" + base64);
                return remoteBot.executeAction("set_qq_avatar", payload.toJSONString());
            } catch (Exception e) {
                log.error("设置QQ头像失败", e);
                return "设置QQ头像失败: " + e.getMessage();
            }
        }


        @Tool(description = "查看此账号的定时(主动续火)任务列表")
        public String list_cron_tasks(@ToolParam(description = "BID") Long bid) {
            QueryWrapper<CronMessage> qw = new QueryWrapper<>();
            qw.eq("qid", String.valueOf(bid));
            java.util.List<CronMessage> list = cronMapper.selectList(qw);
            if (list == null || list.isEmpty()) {
                return "当前账号没有定时任务";
            }
            StringBuilder sb = new StringBuilder("定时任务列表：\n");
            for (CronMessage msg : list) {
                sb.append(String.format("ID: %s, 目标: %s, Cron: %s, 描述: %s, 内容: %s\n",
                        msg.getId(), msg.getTargetId(), msg.getCron(), msg.getDesc(), msg.getMsg()));
            }
            return sb.toString();
        }

        @Tool(description = "添加定时(主动续火)任务,禁止添加秒分时级的循环任务")
        public String add_cron_task(
                @ToolParam(description = "BID") Long bid,
                @ToolParam(description = "目标ID（如群号加前缀 g123456，私聊加前缀 u123456）") String targetId,
                @ToolParam(description = "Cron 表达式（如 0 0 12 * * ?）") String cron,
                @ToolParam(description = "任务中文描述") String desc,
                @ToolParam(description = "要发送的消息内容") String msg) {
            CronMessage cronMessage = new CronMessage();
            cronMessage.setQid(String.valueOf(bid));
            cronMessage.setCron(cron);
            cronMessage.setTargetId(targetId);
            cronMessage.setMsg(msg);
            cronMessage.setDesc(desc);

            int state = cronMapper.insert(cronMessage);
            if (state > 0) {
                QueryWrapper<CronMessage> qw = new QueryWrapper<>();
                qw.eq("qid", String.valueOf(bid))
                        .eq("cron", cron)
                        .eq("desc", desc)
                        .eq("target_id", targetId)
                        .eq("msg", msg);
                CronMessage savedMsg = cronMapper.selectOne(qw);
                if (savedMsg != null) {
                    cronMessage.setId(savedMsg.getId());
                    cronService.appendTask(cronMessage);
                    return "添加成功，任务ID：" + savedMsg.getId();
                }
                return "添加成功，但未获取到新任务ID";
            }
            return "添加失败";
        }

        @Tool(description = "删除定时(主动续火)任务")
        public String delete_cron_task(
                @ToolParam(description = "定时任务ID") String id) {
            try {
                cronService.del(id);
                return "删除成功，已移除任务ID：" + id;
            } catch (Exception e) {
                log.error("删除定时任务出错", e);
                return "删除失败: " + e.getMessage();
            }
        }

        //get_profile_like
        //{
        //  "user_id": "",
        //  "start": 0,
        //  "count": 10
        //}
        @Tool(description = "获取名片点赞信息")
        public String get_profile_like(@ToolParam(description = "BID") Long bid) {
            BotResolveResult botResult = resolveRemoteBot(bid);
            if (!botResult.success()) {
                return botResult.errorMessage();
            }
            RemoteBot remoteBot = botResult.remoteBot();
            try {
                String result = remoteBot.executeAction("get_profile_like", "{\n" +
                        "  \"user_id\": \"\",\n" +
                        "  \"start\": 0,\n" +
                        "  \"count\": 10\n" +
                        "}");
                VoteResponseDTO responseDTO = VoteResponseDTO.parseFromJson(result);
                return JSON.toJSONString(responseDTO.toSimplifiedData());
            } catch (Exception e) {
                log.error("获取名片点赞信息出错", e);
                return "获取名片点赞信息出错: " + e.getMessage();
            }
        }

        //send_like
        //{
        //    "user_id": "123456,234567",
        //    "times": 10
        //}
        @Tool(description = "给指定QQ名片点赞")
        public String send_like(
                @ToolParam(description = "BID") Long bid,
                @ToolParam(description = "QQID,可多个用英文逗号分隔") String userIds,
                @ToolParam(description = "点赞次数,一般10次 svip20次") Integer times) {
            if (userIds == null || userIds.trim().isEmpty()) {
                return "QQID不能为空";
            }

            BotResolveResult botResult = resolveRemoteBot(bid);
            if (!botResult.success()) {
                return botResult.errorMessage();
            }
            RemoteBot remoteBot = botResult.remoteBot();
            String[] userIdArray = userIds.split("[,，]");
            List<String> results = new ArrayList<>();
            for (String userIdText : userIdArray) {
                String trimmedUserId = userIdText.trim();
                if (trimmedUserId.isEmpty()) {
                    continue;
                }
                try {
                    Long.parseLong(trimmedUserId);
                } catch (NumberFormatException e) {
                    results.add("QQID格式错误: " + trimmedUserId);
                    continue;
                }
                try {
                    String result = remoteBot.executeAction("send_like", "{\n" +
                            "    \"user_id\": \"" + trimmedUserId + "\",\n" +
                            "    \"times\": " + times + "\n" +
                            "}");
                    results.add(trimmedUserId + ": " + result);
                } catch (Exception e) {
                    log.error("给指定QQ名片点赞出错, userId={}", trimmedUserId, e);
                    results.add(trimmedUserId + ": 给指定QQ名片点赞出错: " + e.getMessage());
                }
            }

            if (results.isEmpty()) {
                return "未找到有效的QQID";
            }
            return String.join("\n", results);
        }
    }
}
