package io.github.gdpl2112.dg_bot.service.optionals;

import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.dao.AiConf;
import io.github.gdpl2112.dg_bot.mapper.AiConfMapper;
import io.github.gdpl2112.dg_bot.mapper.CronMapper;
import io.github.gdpl2112.dg_bot.mapper.SaveMapper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
     * 对话记忆缓存，key为bid:subjectId，value为带时间戳的Spring AI Message队列
     */
    private static final Map<String, MemoryEntry> MESSAGE_MEMORY_CACHE = new ConcurrentHashMap<>();

    /**
     * 追踪AI发送的消息内部ID（按bot隔离），用于判断后续引用回复是否指向AI消息，
     * 若引用的是AI消息则无需前缀即可触发AI对话。
     * key: botId, value: 该bot最近发送的AI消息的内部ID集合（上限20000条，超出淘汰最早）
     */
    private static final Map<Long, LinkedHashSet<Integer>> AI_SENT_MSG_IDS = new ConcurrentHashMap<>();

    /**
     * 记忆缓存过期时间（毫秒），默认1天无活动自动清理
     */
    private static final long MEMORY_TTL_MILLIS = 24 * 60 * 60 * 1000L;

    /**
     * 定时清理过期记忆的调度器
     */
    private static final ScheduledExecutorService MEMORY_CLEANER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ai-memory-cleaner");
        t.setDaemon(true);
        return t;
    });

    /**
     * 按bot隔离的单线程执行器，保证同一bot的AI请求严格顺序执行（上次完成再进行下次）。
     * 执行器随应用生命周期持有，daemon线程随JVM退出自动清理，无需手动关闭。
     */
    @SuppressWarnings("resource")
    private static final Map<Long, ExecutorService> BOT_AI_EXECUTORS = new ConcurrentHashMap<>();

    static {
        MEMORY_CLEANER.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, MemoryEntry>> it = MESSAGE_MEMORY_CACHE.entrySet().iterator();
            int cleaned = 0;
            while (it.hasNext()) {
                Map.Entry<String, MemoryEntry> entry = it.next();
                if (now - entry.getValue().lastAccessTime > MEMORY_TTL_MILLIS) {
                    it.remove();
                    cleaned++;
                }
            }
            if (cleaned > 0) {
                log.debug("清理过期记忆缓存 {} 条", cleaned);
            }
        }, 12, 12, TimeUnit.HOURS);
    }

    /**
     * 记忆缓存条目，包含消息队列和最后访问时间戳
     */
    private static class MemoryEntry {
        final Deque<Message> messages;
        volatile long lastAccessTime;

        MemoryEntry() {
            this.messages = new ArrayDeque<>();
            this.lastAccessTime = System.currentTimeMillis();
        }

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

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

    @Autowired
    private SaveMapper saveMapper;

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
            你是一个集成在QQ代挂服务中的助手,你的名字是%s,你的ID是%s
            回复与问题或与QQ相关的内容,用户设定:%s
            严禁输出任何markdown格式 允许使用表情符号,颜文字和文本
            当前接收到的消息变量,%sId%s中id为%s发送的消息
            回复必须使用与用户输入相同的语言,且不得使用其他BotID,优先使用已有tools后再回答
            """;

    /**
     * 解析后的消息结果
     */
    private record ParsedMessage(String content, String quotedText, boolean isQuotingAiMessage) {}

    @Override
    public void run(MessageEvent event) {
        Long bid = event.getBot().getId();
        Long sid = event.getSender().getId();

        // 校验发送者是否为具有最高权限的号主
        if (bid.longValue() != sid.longValue() && !defaultService.isAdmin(bid, sid)) {
            return;
        }

        List<Image> images = new ArrayList<>();
        ParsedMessage parsed = parseIncomingMessage(event, bid, images);
        if (parsed == null) {
            return;
        }
        String content = parsed.content();
        String quotedText = parsed.quotedText();
        boolean isQuotingAiMessage = parsed.isQuotingAiMessage();

        // 提取并获取AI全局配置
        AiConf aiConf = aiConfMapper.selectById(String.valueOf(bid));
        if (aiConf == null || !aiConf.getOpen()) {
            return;
        }

        String aiPrefix = aiConf.getPrefix() != null ? aiConf.getPrefix() : "AI";

        // 提取和判断是否属于AI助手指令前缀，如果是则进行处理
        // 若引用的是AI此前发送的消息，则无需带前缀也可触发
        boolean b = !content.toLowerCase().startsWith(aiPrefix.toLowerCase());
        if (b) {
            if (!isQuotingAiMessage) {
                return;
            }
        }

        // 提取去除前缀后的实际对话内容（引用AI消息免前缀时直接使用完整内容）
        String actualContent;
        if (isQuotingAiMessage && b) {
            actualContent = content;
        } else {
            actualContent = content.substring(aiPrefix.length()).trim();
        }
        if (actualContent.isEmpty()) {
            return;
        }

        // 若存在引用消息，则将被引用内容作为上下文追加到实际对话内容前
        if (quotedText != null && !quotedText.isEmpty()) {
            actualContent = actualContent + "\n\n[引用]" + quotedText;
        }

        if ("清除记忆".equalsIgnoreCase(actualContent) || "clear".equalsIgnoreCase(actualContent)) {
            MESSAGE_MEMORY_CACHE.remove(buildMemoryKey(bid, event.getSubject().getId()));
            BID_LAST_IMAGE_MAP.remove(bid);
            event.getSubject().sendMessage("[AI功能输出]\n已清除记忆");
            return;
        }

        log.info("AI助手对话 bid:{} sid:{} content:{}", bid, sid, actualContent);

        String systemPrompt = String.format(SYSTEM_PROMPT_TEXT, aiConf.getName(), event.getBot().getId(), aiConf.getTrait(),
                event instanceof GroupAwareMessageEvent ? "群聊" : "私信", event.getSubject().getId(), event.getSender().getId());
        String memoryKey = buildMemoryKey(bid, event.getSubject().getId());
        appendMemory(memoryKey, buildUserMessage(actualContent, images), resolveMaxMessage(aiConf.getMaxMessage()));
        List<Message> memoryMessages = buildMemoryMessages(memoryKey, resolveMaxMessage(aiConf.getMaxMessage()));

        ChatClient chatClient = getChatClient(aiConf);
        String finalActualContent = actualContent;
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = chatClient.prompt().system(systemPrompt).messages(memoryMessages).user(userSpec -> {
            userSpec.text(finalActualContent);
            for (Image img : images) {
                try {
                    String url = Image.queryUrl(img);
                    userSpec.media(MimeTypeUtils.IMAGE_JPEG, new UrlResource(URI.create(url)));
                } catch (Exception e) {
                    log.warn("添加图片到AI消息失败", e);
                }
            }
        }).toolCallbacks(mergeToolCallbacks(aiConf.getNetwork()));

        BOT_AI_EXECUTORS.computeIfAbsent(bid, k ->
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "ai-call-bot-" + k);
                    t.setDaemon(true);
                    return t;
                })
        ).submit(() -> {
            try {
                String responseContent = chatClientRequestSpec.call().content();
                if (responseContent == null || responseContent.trim().isEmpty()) {
                    return;
                }
                String replyPrefix = "['" + aiConf.getName() + "'回答]\n\n";
                MessageChain responseChain = DgSerializer.stringDeserializeToMessageChain(responseContent, event.getBot(), event.getSubject());
                MessageChainBuilder mcb = new MessageChainBuilder();
                mcb.append(new QuoteReply(event.getMessage()));
                mcb.append(new PlainText(replyPrefix));
                if (responseChain != null) {
                    mcb.append(responseChain);
                } else {
                    mcb.append(new PlainText(responseContent));
                }
                var receipt = event.getSubject().sendMessage(mcb.build());
                recordAiSentMessageIds(bid, receipt);
                appendMemory(memoryKey, new AssistantMessage(responseContent.trim()), resolveMaxMessage(aiConf.getMaxMessage()));
            } catch (Exception e) {
                log.error("AI助手对话调用失败 bid:{} sid:{}", bid, sid, e);
                event.getSubject().sendMessage(new PlainText("[" + aiConf.getName() + "输出]\n\n[AI调用失败，请稍后再试]"));
            }
        });
    }

    /**
     * 解析入站消息：提取图片、引用消息、序列化文本内容。
     *
     * @param event  消息事件
     * @param bid    机器人ID
     * @param images 输出参数，收集消息中的图片
     * @return 解析结果，若内容为空则返回 null
     */
    private ParsedMessage parseIncomingMessage(MessageEvent event, Long bid, List<Image> images) {
        // 提取消息中的所有图片，供AI多模态对话和set_qq_avatar工具使用
        for (SingleMessage singleMessage : event.getMessage()) {
            if (singleMessage instanceof Image img) {
                images.add(img);
            }
        }
        if (!images.isEmpty()) {
            BID_LAST_IMAGE_MAP.put(bid, images.getLast());
        }

        // 提取引用消息（QuoteReply）中的文本与图片
        String quotedText = null;
        boolean isQuotingAiMessage = false;
        for (SingleMessage singleMessage : event.getMessage()) {
            if (singleMessage instanceof QuoteReply qr) {
                try {
                    int[] quotedIds = qr.getSource().getInternalIds();
                    Set<Integer> aiSentIds = AI_SENT_MSG_IDS.get(bid);
                    if (aiSentIds != null && quotedIds != null) {
                        synchronized (aiSentIds) {
                            for (int id : quotedIds) {
                                if (aiSentIds.contains(id)) {
                                    isQuotingAiMessage = true;
                                    break;
                                }
                            }
                        }
                    }

                    MessageChain originalMessage = qr.getSource().getOriginalMessage();
                    for (SingleMessage om : originalMessage) {
                        if (om instanceof Image img) {
                            images.add(img);
                        }
                    }
                    String qt = DgSerializer.messageChainSerializeForAI(originalMessage, event.getBot());
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

        // 序列化文本内容（AI友好格式：文字/图片占位/@昵称/表情）
        String content = DgSerializer.messageChainSerializeForAI(event.getMessage(), event.getBot());
        if (Judge.isEmpty(content)) {
            content = MessageChain.serializeToJsonString(event.getMessage());
        }
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        return new ParsedMessage(content.trim(), quotedText, isQuotingAiMessage);
    }

    /**
     * 记录AI发送的消息ID，后续被引用时可免前缀触发AI
     */
    private static void recordAiSentMessageIds(Long bid, Object receipt) {
        try {
            if (receipt == null) return;
            // 通过反射获取 MessageReceipt.getSource().getInternalIds()
            var sourceMethod = receipt.getClass().getMethod("getSource");
            var source = sourceMethod.invoke(receipt);
            if (source == null) return;
            var idsMethod = source.getClass().getMethod("getInternalIds");
            int[] ids = (int[]) idsMethod.invoke(source);
            if (ids == null || ids.length == 0) return;

            Set<Integer> idSet = AI_SENT_MSG_IDS.computeIfAbsent(bid, k -> new LinkedHashSet<>(20000));
            synchronized (idSet) {
                for (int id : ids) {
                    idSet.add(id);
                }
                while (idSet.size() > 10) {
                    Iterator<Integer> it = idSet.iterator();
                    it.next();
                    it.remove();
                }
            }
        } catch (Exception ex) {
            log.warn("记录AI发送消息ID失败", ex);
        }
    }

    private static String buildMemoryKey(Long bid, Long subjectId) {
        return bid + ":" + subjectId;
    }

    private static int resolveMaxMessage(Integer maxMessage) {
        return maxMessage == null || maxMessage <= 0 ? 30 : maxMessage;
    }

    /**
     * 根据记忆缓存获取最近的消息列表
     *
     * @param memoryKey  记忆缓存key，非空
     * @param maxMessage 最大记忆条数
     * @return 消息列表，无记忆时返回空列表
     */
    private static List<Message> buildMemoryMessages(String memoryKey, int maxMessage) {
        MemoryEntry entry = MESSAGE_MEMORY_CACHE.get(memoryKey);
        if (entry == null || entry.messages.isEmpty()) {
            return Collections.emptyList();
        }

        // 更新最后访问时间
        entry.touch();

        List<Message> messages = new ArrayList<>();
        synchronized (entry.messages) {
            int start = Math.max(0, entry.messages.size() - maxMessage);
            int index = 0;
            for (Message msg : entry.messages) {
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
        MemoryEntry entry = MESSAGE_MEMORY_CACHE.computeIfAbsent(memoryKey, k -> new MemoryEntry());
        entry.touch();
        synchronized (entry.messages) {
            entry.messages.addLast(message);
            while (entry.messages.size() > maxMessage) {
                entry.messages.removeFirst();
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
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024; // 5MB

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
                if (bytes.length > MAX_IMAGE_BYTES) {
                    log.warn("图片过大({} bytes)，已跳过", bytes.length);
                    continue;
                }

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
     * 合并内置工具与可选的MCP联网工具（避免覆盖），若开启联网则两者均注册。
     *
     * @param network 是否开启联网
     * @return 合并后的 ToolCallback 数组
     */
    private ToolCallback[] mergeToolCallbacks(boolean network) {
        ToolCallback[] builtin = getToolCallbacks();
        if (!network) {
            return builtin;
        }
        ToolCallback[] mcp = mcpBean.getToolCallbacks();
        if (mcp == null || mcp.length == 0) {
            return builtin;
        }
        ToolCallback[] merged = Arrays.copyOf(builtin, builtin.length + mcp.length);
        System.arraycopy(mcp, 0, merged, builtin.length, mcp.length);
        return merged;
    }

    /**
     * 提取本助手所有 @Tool 方法对应的 ToolCallback，交由主模型原生 function-calling 自行选择。
     * 工具集固定，首次构建后缓存复用，避免每次请求重建。
     *
     * @return ToolCallback 数组
     */
    public ToolCallback[] getToolCallbacks() {
        if (cachedToolCallbacks == null) {
            synchronized (this) {
                if (cachedToolCallbacks == null) {
                    if (aiAssistantOptional == null) {
                        aiAssistantOptional = new AiAssistantOptionalTools(cronMapper, cronService, saveMapper);
                    }
                    cachedToolCallbacks = ToolCallbacks.from(aiAssistantOptional);
                }
            }
        }
        return cachedToolCallbacks;
    }

    private AiAssistantOptionalTools aiAssistantOptional;

    private volatile ToolCallback[] cachedToolCallbacks;

}
