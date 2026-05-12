package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.dao.AiConf;
import io.github.gdpl2112.dg_bot.mapper.AiConfMapper;
import io.github.gdpl2112.dg_bot.mapper.CronMapper;
import io.github.gdpl2112.dg_bot.service.CronService;
import io.github.gdpl2112.dg_bot.service.listenerhosts.DefaultService;
import io.github.gdpl2112.dg_bot.utils.HttpsUtils;
import io.github.kloping.judge.Judge;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.event.events.GroupAwareMessageEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;
import top.kloping.core.ai.McpBean;

import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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
     * 对话记忆缓存，key为bid:subjectId，value为Spring AI Message队列
     */
    private static final Map<String, Deque<Message>> MESSAGE_MEMORY_CACHE = new ConcurrentHashMap<>();

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

    private static final String SYSTEM_PROMPT_TEXT = """
            You are an assistant integrated into the QQ proxy service. Your name is %s. You can only reply with content related to the question or related to QQ.
            It is strictly prohibited to output any Markdown format. Only emoji, smiley symbols and text are allowed.
            Style: %s. Current environment: %s, Environment ID: %s, Sender ID: %s, Robot ID: %s
            Replies must be in the same language as the user's input and must not switch to other BotIDs.
            """;

    @Override
    public void run(MessageEvent event) {
        Long bid = event.getBot().getId();
        Long sid = event.getSender().getId();

        // 校验发送者是否为具有最高权限的号主
        if (bid.longValue() != sid.longValue() && !defaultService.isAdmin(bid, sid)) {
            return;
        }

        // 提取消息中的所有图片，供AI多模态对话和set_qq_avatar工具使用
        List<Image> images = new ArrayList<>();
        for (SingleMessage singleMessage : event.getMessage()) {
            if (singleMessage instanceof Image img) {
                images.add(img);
            }
        }
        if (!images.isEmpty()) {
            BID_LAST_IMAGE_MAP.put(bid, images.getLast());
        }

        // 提取引用消息（QuoteReply）中的文本与图片，一同传入AI上下文
        String quotedText = null;
        for (SingleMessage singleMessage : event.getMessage()) {
            if (singleMessage instanceof QuoteReply qr) {
                try {
                    MessageChain originalMessage = qr.getSource().getOriginalMessage();
                    // 收集引用消息中的图片到多模态输入
                    for (SingleMessage om : originalMessage) {
                        if (om instanceof Image img) {
                            images.add(img);
                        }
                    }
                    // 将引用消息序列化为纯文本
                    String qt = DgSerializer.messageChainSerializeToString(originalMessage);
                    if (Judge.isEmpty(qt)) {
                        qt = MessageChain.serializeToJsonString(originalMessage);
                    }
                    quotedText = qt == null ? null : qt.trim();
                } catch (Exception e) {
                    log.warn("解析引用消息失败", e);
                }
                break;
            }
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
        if (!content.toLowerCase().contains(aiPrefix.toLowerCase())) {
            return;
        }

        // 提取去除前缀后的实际对话内容
        String actualContent = content.replace(aiPrefix, "").trim();
        if (actualContent.isEmpty()) {
            return;
        }

        // 若存在引用消息，则将被引用内容作为上下文追加到实际对话内容前
        if (quotedText != null && !quotedText.isEmpty()) {
            actualContent = actualContent + "\n\n[引用]" + quotedText;
        }

        if ("清除记忆".equalsIgnoreCase(actualContent) || "clear".equalsIgnoreCase(actualContent)) {
            MESSAGE_MEMORY_CACHE.remove(buildMemoryKey(bid, event.getSubject().getId()));
            //images
            BID_LAST_IMAGE_MAP.remove(bid);
            event.getSubject().sendMessage("[AI功能输出]\n已清除记忆");
            return;
        }

        log.info("AI助手对话 bid:{} sid:{} content:{}", bid, sid, actualContent);

        String systemPrompt = String.format(SYSTEM_PROMPT_TEXT, aiConf.getName(), aiConf.getTrait(), event instanceof GroupAwareMessageEvent ? "Group" : "Friend", event.getSubject().getId(), event.getSender().getId(), event.getBot().getId());
        String memoryKey = buildMemoryKey(bid, event.getSubject().getId());
        appendMemory(memoryKey, buildUserMessage(actualContent, images), resolveMaxMessage(aiConf.getMaxMessage()));
        List<Message> memoryMessages = buildMemoryMessages(memoryKey, resolveMaxMessage(aiConf.getMaxMessage()));

        ChatClient chatClient = getChatClient(aiConf);
        String finalActualContent = actualContent;
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = chatClient.prompt().system(systemPrompt).messages(memoryMessages).user(userSpec -> {
            userSpec.text(finalActualContent);
            // 将消息中的图片作为多模态内容填入AI消息
            for (Image img : images) {
                try {
                    String url = Image.queryUrl(img);
                    userSpec.media(MimeTypeUtils.IMAGE_JPEG, new UrlResource(URI.create(url)));
                } catch (Exception e) {
                    log.warn("添加图片到AI消息失败", e);
                }
            }
        }).toolCallbacks(getToolCallbacks(aiConf, memoryKey));
        if (aiConf.getNetwork()) {
            chatClientRequestSpec.toolCallbacks(mcpBean.getToolCallbacks());
        }

        try {
            List<String> responseChunks = chatClientRequestSpec.stream().content().collectList().block();
            if (responseChunks == null || responseChunks.isEmpty()) {
                return;
            }
            String responseContent = String.join("", responseChunks);
            String reply = "['" + aiConf.getName() + "'回答]\n\n" + responseContent;
            // 回复时携带原消息引用
            MessageChainBuilder mcb = new MessageChainBuilder();
            mcb.append(new QuoteReply(event.getMessage()));
            mcb.append(new PlainText(reply));
            event.getSubject().sendMessage(mcb.build());
            appendMemory(memoryKey, new AssistantMessage(responseContent.trim()), resolveMaxMessage(aiConf.getMaxMessage()));
        } catch (Exception e) {
            log.error("AI助手对话调用失败 bid:{} sid:{}", bid, sid, e);
            event.getSubject().sendMessage(new PlainText("[" + aiConf.getName() + "输出]\n\n[AI调用失败，请稍后再试]"));
        }
    }

    private static String buildMemoryKey(Long bid, Long subjectId) {
        return bid + ":" + subjectId;
    }

    private static int resolveMaxMessage(Integer maxMessage) {
        return maxMessage == null || maxMessage <= 0 ? 10 : maxMessage;
    }

    /**
     * 根据记忆缓存获取最近的消息列表
     *
     * @param memoryKey  记忆缓存key，非空
     * @param maxMessage 最大记忆条数
     * @return 消息列表，无记忆时返回空列表
     */
    private static List<Message> buildMemoryMessages(String memoryKey, int maxMessage) {
        Deque<Message> memory = MESSAGE_MEMORY_CACHE.get(memoryKey);
        if (memory == null || memory.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> messages = new ArrayList<>();
        synchronized (memory) {
            int start = Math.max(0, memory.size() - maxMessage);
            int index = 0;
            for (Message msg : memory) {
                if (index++ < start) {
                    continue;
                }
                messages.add(msg);
            }
        }
        return messages;
    }

    /**
     * 向记忆缓存追加一条消息，超出maxMessage时淘汰最早的消息
     *
     * @param memoryKey  记忆缓存key，非空
     * @param message    Spring AI消息，非空
     * @param maxMessage 最大记忆条数
     */
    private static void appendMemory(String memoryKey, Message message, int maxMessage) {
        Deque<Message> memory = MESSAGE_MEMORY_CACHE.computeIfAbsent(memoryKey, k -> new ArrayDeque<>());
        synchronized (memory) {
            memory.addLast(message);
            while (memory.size() > maxMessage) {
                memory.removeFirst();
            }
        }
    }

    /**
     * 构建包含文本和图片的UserMessage
     *
     * @param text   文本内容，非空
     * @param images 图片列表，可为空
     * @return UserMessage实例
     */
    private static UserMessage buildUserMessage(String text, List<Image> images) {
        if (images == null || images.isEmpty()) {
            return new UserMessage(text);
        }
        List<org.springframework.ai.content.Media> mediaList = new ArrayList<>();
        List<String> md5s = new ArrayList<>();
        for (Image img : images) {
            try {
                String url = Image.queryUrl(img);
                byte[] bytes = HttpsUtils.readAsBytesFromImageUrl(url);
                if (bytes == null) continue;

                String md5 = org.springframework.util.DigestUtils.md5DigestAsHex(bytes);
                if (md5s.contains(md5)) {
                    log.debug("图片已存在，跳过");
                    continue;
                }
                md5s.add(md5);
                mediaList.add(new Media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(bytes)));
            } catch (Exception e) {
                log.warn("构建图片Media失败", e);
            }
        }
        return mediaList.isEmpty() ? new UserMessage(text) : UserMessage.builder().text(text).media(mediaList).build();
    }


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

    private static final RetryTemplate RETRY_TEMPLATE = new RetryTemplate(new RetryPolicy() {
        @Override
        public boolean shouldRetry(Throwable throwable) {
            log.error("ChatClient call failed", throwable);
            return false;
        }
    });

    private static final RestClient.Builder REST_CLIENT_BUILDER = RestClient.builder().requestFactory(ClientHttpRequestFactoryBuilder.simple().build(HttpClientSettings.defaults().withTimeouts(Duration.of(5, ChronoUnit.SECONDS), Duration.of(90, ChronoUnit.SECONDS))));

    /**
     * 根据AI配置创建ChatClient实例
     *
     * @param aiConf AI配置，非空
     * @return 新创建的ChatClient实例
     */
    private static ChatClient createChatClient(AiConf aiConf) {
        OpenAiApi openAiApi = OpenAiApi.builder().baseUrl(aiConf.getBaseUrl()).apiKey(aiConf.getApiKey()).restClientBuilder(REST_CLIENT_BUILDER).build();

        OpenAiChatOptions options = OpenAiChatOptions.builder().model(aiConf.getModelId()).temperature(aiConf.getTemperature()).build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(options).retryTemplate(RETRY_TEMPLATE).build();

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
        // 取最近5条对话记录作为工具选择的上下文
        List<Message> recentMemoryMessages = buildMemoryMessages(memoryKey, 5);

        String systemPrompt = initToolCache();
        if (systemPrompt == null) {
            log.error("工具列表缓存初始化失败");
            return new ArrayList<>(cachedToolName2tool.values());
        }

        String userPrompt = recentMemoryMessages.isEmpty() ? "暂无对话记录" : "根据以上对话记录选择工具";

        String result;
        try {
            ChatClient chatClient = getChatClient(conf);
            List<String> resultChunks = chatClient.prompt().system(systemPrompt).messages(recentMemoryMessages).user(userPrompt).stream().content().collectList().block();
            result = resultChunks == null ? null : String.join("", resultChunks);
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
        List<String> extractedUrls = new ArrayList<>();
        for (int i = tools.size() - 1; i >= 0; i--) {
            String item = tools.getString(i);
            ToolCallback tool = cachedToolName2tool.get(item);
            if (tool == null) {
                log.warn("toolName not found: {}. on conf: {}", item, conf.getQid());
            } else {
                toolCallbacks.add(tool);
            }
        }
        // 如果AI没选中任何工具则降级返回全部
        // return toolCallbacks.isEmpty() ? new ArrayList<>(cachedToolName2tool.values()) : toolCallbacks;
        return new ArrayList<>(cachedToolName2tool.values());
    }


    /**
     * 将 AiAssistantOptional 中所有 @Tool 方法逐一提取为独立的 ToolCallback
     *
     * @return ToolCallback 数组，每个元素对应一个 @Tool 方法
     */
    public ToolCallback[] getToolCallbacks() {
        if (aiAssistantOptional == null) {
            aiAssistantOptional = new AiAssistantOptionalTools(cronMapper, cronService);
        }
        return ToolCallbacks.from(aiAssistantOptional);
    }

    private AiAssistantOptionalTools aiAssistantOptional;

    /**
     * 工具名称->描述 缓存，由 @Tool 方法固定生成，无需每次重建
     */
    private Map<String, String> cachedToolName2Desc;
    /**
     * 工具名称->ToolCallback 缓存，由 @Tool 方法固定生成，无需每次重建
     */
    private Map<String, ToolCallback> cachedToolName2tool;

    private String toolListPrompt;

    /**
     * 懒加载初始化工具缓存，首次调用时从 @Tool 方法提取并缓存
     */
    private synchronized String initToolCache() {
        if (cachedToolName2Desc != null) return null;
        if (toolListPrompt != null) return toolListPrompt;
        cachedToolName2Desc = new HashMap<>();
        cachedToolName2tool = new HashMap<>();
        for (ToolCallback toolCallback : getToolCallbacks()) {
            cachedToolName2Desc.put(toolCallback.getToolDefinition().name(), toolCallback.getToolDefinition().description());
            cachedToolName2tool.put(toolCallback.getToolDefinition().name(), toolCallback);
        }
        String cachedToolListData = JSON.toJSONString(cachedToolName2Desc);
        toolListPrompt = """
                You are a tool selector. Based on the user's recent conversation history,
                select the names of the tools that might be needed from the available tool list.
                Output a JSON array with only the tool names as elements. Do not include any other text.
                If no tools or images are needed, output an empty array [].
                Example output: ["set_group_card"]
                Available tool list (name -> description):
                """ + cachedToolListData;
        return toolListPrompt;
    }

}
