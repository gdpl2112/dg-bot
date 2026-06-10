package io.github.gdpl2112.dg_bot.built;

import com.alibaba.fastjson.JSON;
import io.github.gdpl2112.dg_bot.dao.AllMessage;
import io.github.gdpl2112.dg_bot.service.optionals.ShortVideoParse;
import io.github.kloping.arr.ArrSerializer;
import io.github.kloping.url.UrlUtils;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.utils.ExternalResource;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author github.kloping
 */
@Slf4j
public class DgSerializer {
    public static final Map<Integer, MarketFace> MARKET_FACE_MAP = new HashMap<>();
    public static final RestTemplate TEMPLATE = new RestTemplate();
    public static final ArrSerializer ARR_SERIALIZER = new ArrSerializer();
    private static final Pattern PATTER_FACE = Pattern.compile("<face:.*?>");
    private static final Pattern PATTER_PIC = Pattern.compile("<pic:.*?>");
    private static final Pattern PATTER_AT = Pattern.compile("<at:\\d+>");
    private static final Pattern PATTER_MUSIC = Pattern.compile("<music:.*?>");
    private static final Pattern PATTER_VOICE = Pattern.compile("<audio:.*?>");
    private static final Pattern PATTER_MIRAI_FACE = Pattern.compile("\\[mirai:face:\\d+]");
    private static final Pattern PATTER_MIRAI_IMAGE = Pattern.compile("\\[mirai:image:[a-zA-Z0-9\\-]+]");
    public static final Pattern[] PATTERNS = {PATTER_FACE, PATTER_PIC, PATTER_AT, PATTER_VOICE, PATTER_MUSIC, PATTER_MIRAI_FACE, PATTER_MIRAI_IMAGE};
    private static final String BASE64 = "base64,";
    private static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient();

    static {
        ARR_SERIALIZER.add(new ArrSerializer.Rule<Image>(Image.class) {
            @Override
            public String serializer(Image o) {
                return String.format("<pic:%s>", o.getImageId());
            }
        });
        ARR_SERIALIZER.add(new ArrSerializer.Rule<At>(At.class) {
            @Override
            public String serializer(At o) {
                return String.format("<at:%s>", o.getTarget());
            }
        });
        ARR_SERIALIZER.add(new ArrSerializer.Rule<Face>(Face.class) {
            @Override
            public String serializer(Face o) {
                return String.format("<face:%s>", o.getId());
            }
        });
        ARR_SERIALIZER.add(new ArrSerializer.Rule<PlainText>(PlainText.class) {
            @Override
            public String serializer(PlainText o) {
                String touch = o.getContent();
                String regx = "<.*?>";
                Pattern pattern = Pattern.compile(regx);
                Matcher matcher = pattern.matcher(touch);
                while (matcher.find()) {
                    touch = touch.replace(matcher.group(), "\\" + matcher.group());
                }
                return touch;
            }
        });
//        ARR_SERIALIZER.add(new ArrSerializer.Rule<ShortVideo>(ShortVideo.class) {
//            @Override
//            public String serializer(ShortVideo o) {
//                if (o instanceof OnlineShortVideo onlineShortVideo) {
//                    return String.format("<video:%s>", onlineShortVideo.getUrlForDownload());
//                } else {
//                    log.warn("[DgSerializer] short video is not online short video");
//                    return "";
//                }
//            }
//        });
        ARR_SERIALIZER.add(new ArrSerializer.Rule<Audio>(Audio.class) {
            @Override
            public String serializer(Audio o) {
                return String.format("<audio:%s>", o.getFilename());
            }
        });
        ARR_SERIALIZER.add(new ArrSerializer.Rule<MusicShare>(MusicShare.class) {
            @Override
            public String serializer(MusicShare o) {
                return String.format("<music:%s>", o.getMusicUrl());
            }
        });
        ARR_SERIALIZER.add(new ArrSerializer.Rule<MarketFace>(MarketFace.class) {
            @Override
            public String serializer(MarketFace o) {
                MARKET_FACE_MAP.put(o.getId(), o);
                return String.format("<marketface:%s>", o.getId());
            }
        });
        ARR_SERIALIZER.add(new ArrSerializer.Rule<QuoteReply>(QuoteReply.class) {
            @Override
            public String serializer(QuoteReply o) {
                return String.format("<qr:%s>", AllMessage.latest(0, o.getSource().getInternalIds()));
            }
        });
        ARR_SERIALIZER.setMode(1);
    }

    public static MessageChain stringDeserializeToMessageChain(String str, Bot bot) {
        return stringDeserializeToMessageChain(str, bot, bot.getAsFriend());
    }

    public static MessageChain stringDeserializeToMessageChain(String str, Bot bot, Contact contact) {
        if (str == null || str.isEmpty() || bot == null) return null;
        MessageChainBuilder builder = new MessageChainBuilder();
        goToFormat(str, builder, bot, contact);
        MessageChain message = builder.build();
        return message;
    }

    private static List<Object> goToFormat(String sb, MessageChainBuilder builder, Bot bot, Contact contact) {
        List<Object> allElements = getAllElements(sb);
        for (Object o : allElements) {
            String str = o.toString();
            boolean k = (str.startsWith("<") || str.startsWith("[")) && !str.matches("\\[.+]请使用最新版手机QQ体验新功能");
            if (k) {
                Message msg = null;
                String ss = str.replaceAll("[<>\\[\\]]", "");
                int i1 = ss.indexOf(":");
                String s1 = ss.substring(0, i1);
                String s2 = ss.substring(i1 + 1);
                switch (s1.toLowerCase()) {
                    case "pic":
                        msg = createImage(bot.getAsFriend(), bot, s2);
                        break;
                    case "face":
                        msg = new Face(Integer.parseInt(s2));
                        break;
                    case "at":
                        long tid = -1L;
                        if (s2.contains("?")) tid = contact.getId();
                        else tid = Long.parseLong(s2);
                        msg = new At(tid);
                        break;
                    case "voice":
                    case "audio":
                        msg = createVoiceMessageInGroup(s2, bot.getId(), bot.getAsFriend());
                        break;
                    case "video":
                        //s2 is download url
                        //  ShortVideo shortVideo = event.getSubject().uploadShortVideo(
                        //                            ExternalResource.create(coverBytes),
                        //                            ExternalResource.create(bytes),
                        //                            title + ".mp4"
                        //                    );
                        try {
                            byte[] bytes = ShortVideoParse.downloadBytesWithOkHttp(s2, "");
                            msg = contact.uploadShortVideo(ExternalResource.create(bytes), ExternalResource.create(bytes), "buildfor.mp4");
                        } catch (IOException e) {
                            log.error("download bytes error", e);
                        }
                        break;
                    case "music":
                        msg = createMusic(bot, s2);
                        break;
                    case "marketface":
                        msg = MARKET_FACE_MAP.get(Integer.parseInt(s2));
                        break;
                    case "mirai":
                        String type = s2.substring(0, s2.indexOf(":"));
                        String c0 = s2.substring(s2.indexOf(":") + 1, s2.length());
                        if (type.equals("face"))
                            msg = new Face(Integer.parseInt(c0));
                        else if (type.equals("image"))
                            msg = createImage(contact, bot, c0);
                        break;
                    default:
                        msg = new PlainText(s2);
                        break;
                }
                if (msg != null) builder.append(msg);
            } else {
                builder.append(str);
            }
        }
        return allElements;
    }

    public static List<Object> getAllElements(String line) {
        List<String> list = new ArrayList<>();
        List<Object> olist = new ArrayList<>();
        // 关键逻辑：不再在这里统一转小写，防止破坏 URL 中的大写参数（如 QQ=xxx）
        algorithmFill(list, line);
        for (String s : list) {
            int i = line.toLowerCase().indexOf(s.toLowerCase());
            if (i > 0) {
                olist.add(line.substring(0, i));
            }
            olist.add(line.substring(i, i + s.length()));
            line = line.substring(i + s.length());
        }
        if (!line.isEmpty()) olist.add(line);
        return olist;
    }

    public static void algorithmFill(List<String> list, String line) {
        if (list == null || line == null || line.isEmpty()) return;
        Map<Integer, String> nm = getNearestOne(line, PATTERNS);
        if (nm.isEmpty()) {
            return;
        }
        int n = nm.keySet().iterator().next();
        String v = nm.get(n);

        String pre = line.substring(0, n);
        if (!pre.isEmpty()) {
            list.add(pre);
        }
        list.add(v);

        String next = line.substring(n + v.length());
        algorithmFill(list, next);
    }

    public static Map<Integer, String> getNearestOne(final String line, Pattern... patterns) {
        try {
            Map<Integer, String> map = new LinkedHashMap<>();
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String l1 = matcher.group();
                    int i1 = line.indexOf(l1);
                    map.put(i1, l1);
                }
            }
            Map<Integer, String> result1 = new LinkedHashMap<>();
            map.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEachOrdered(x -> result1.put(x.getKey(), x.getValue()));
            return result1;
        } catch (Exception e) {
            log.error("parseObject error", e);
            return null;
        }
    }

    private static Message createMusic(Bot contact, String vals) {
        String[] ss = vals.split(",");
        MusicKind kind = MusicKind.valueOf(ss[0]);
        MusicShare share = new MusicShare(kind, ss[1], ss[2], ss[3], ss[4], ss[5]);
        return share;
    }

    /**
     * 根据URL获取图片字节数组
     *
     * @param url 图片URL
     * @return 图片字节数组
     */
    public static byte[] getImageFromUrl(String url) {
        // 关键逻辑：某些接口对参数大小写敏感或需要跟随重定向
        // 如果 URL 包含大写参数名，OkHttp 默认不会修改它，但我们需要确保 URL 格式与浏览器完全一致
        Request request = new Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36").build();
        // 关键逻辑：使用自动跟随重定向的客户端
        OkHttpClient client = OK_HTTP_CLIENT.newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            byte[] bytes = response.body().bytes();
            // 调试：保存图片到本地
//            File tempDir = new File("./temp");
//            if (!tempDir.exists()) tempDir.mkdirs();
//            Files.write(Paths.get("./temp/debug_image.png"), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return bytes;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Message createImage(Contact contact, Bot bot, String path) {
        Message image = null;
        try {
            if (path.startsWith("http")) {
                image = Contact.uploadImage(bot.getAsFriend(), new ByteArrayInputStream(getImageFromUrl(path)));
            } else if (path.startsWith("{")) {
                image = Image.fromId(path);
            } else if (path.contains(BASE64)) {
                image = Contact.uploadImage(bot.getAsFriend(), new ByteArrayInputStream(getBase64Data(path)));
            } else if (path.startsWith("[") && path.endsWith("]")) {
                image = createForwardMessageByPic(contact, bot, (String[]) JSON.parseArray(path).toArray(new String[0]));
            } else {
                image = Contact.uploadImage(bot.getAsFriend(), new File(path));
            }
        } catch (Exception e) {
            log.error("图片创建失败:url:{},", path, e);
        }
        if (image != null) return image;
        else return null;
    }

    public static byte[] getBase64Data(String base64) {
        int i = base64.indexOf(BASE64);
        String base64Str = base64.substring(i + BASE64.length());
        byte[] bytes = Base64.getDecoder().decode(base64Str);
        return bytes;
    }

    public static Message createVoiceMessageInGroup(String url, long id, Contact contact) {
        ExternalResource resource = null;
        try {
            byte[] bytes = UrlUtils.getBytesFromHttpUrl(url);
            resource = ExternalResource.create(bytes);
            if (contact instanceof Group) {
                return ((Group) contact).uploadAudio(resource);
            } else if (contact instanceof Friend) {
                return ((Friend) contact).uploadAudio(resource);
            } else return new PlainText(url);
        } catch (Exception e) {
            log.error("uploadAudio error", e);
            return null;
        } finally {
            if (resource != null) {
                try {
                    resource.close();
                } catch (IOException e) {
                    log.error("close resource error", e);
                }
            }
        }
    }

    public static Message createForwardMessageByPic(Contact contact, Bot bot, String[] picUrl) {
        ForwardMessageBuilder builder = new ForwardMessageBuilder(contact);
        for (String s : picUrl) builder.add(bot.getId(), bot.getBot().getNick(), createImage(contact, bot, s));
        return builder.build();
    }

    public static String messageChainSerializeToString(MessageChain chain) {
        return ARR_SERIALIZER.serializer(chain);
    }

    /**
     * 文本优先排序的序列化方法。
     * 遍历 MessageChain，将纯文本内容排在前面，非文本元素（图片、表情、@等）标签排在后面。
     * 保证前缀匹配（如 AI 指令检测）不受非文本元素位置干扰。
     *
     * @param chain 消息链
     * @return 文本优先排序后的序列化字符串
     */
    public static String messageChainSerializeWithTextFirst(MessageChain chain) {
        if (chain == null || chain.isEmpty()) {
            return "";
        }
        StringBuilder textParts = new StringBuilder();
        StringBuilder nonTextParts = new StringBuilder();
        for (SingleMessage singleMessage : chain) {
            if (singleMessage instanceof PlainText) {
                String touch = ((PlainText) singleMessage).getContent();
                // 对尖括号标签进行转义，与 ARR_SERIALIZER 的 PlainText 规则一致
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<.*?>");
                java.util.regex.Matcher matcher = pattern.matcher(touch);
                while (matcher.find()) {
                    touch = touch.replace(matcher.group(), "\\" + matcher.group());
                }
                textParts.append(touch);
            } else {
                // 非文本元素：包装为单元素 MessageChain，复用 ARR_SERIALIZER 的规则序列化
                MessageChain singleChain = new MessageChainBuilder().append(singleMessage).build();
                nonTextParts.append(ARR_SERIALIZER.serializer(singleChain));
            }
        }
        // 文本优先，非文本标签紧跟其后
        return textParts.toString() + nonTextParts.toString();
    }

    /**
     * 面向AI的消息链序列化（文本优先）。
     * 将消息链转为AI易于理解的纯文本：纯文本原样输出，图片替换为"[图片]"、
     *
     * @param chain 消息链
     * @param bot   机器人实例，用于解析 @用户昵称
     * @return AI友好的纯文本
     * @替换为"@昵称"、表情替换为"[表情]"，忽略引用/语音/音乐等无意义标签。
     */
    public static String messageChainSerializeForAI(MessageChain chain, Bot bot) {
        if (chain == null || chain.isEmpty()) {
            return "";
        }
        StringBuilder textParts = new StringBuilder();
        StringBuilder nonTextParts = new StringBuilder();
        for (SingleMessage singleMessage : chain) {
            if (singleMessage instanceof PlainText) {
                String touch = ((PlainText) singleMessage).getContent();
                // 转义尖括号标签，防止被误解析
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<.*?>");
                java.util.regex.Matcher matcher = pattern.matcher(touch);
                while (matcher.find()) {
                    touch = touch.replace(matcher.group(), "\\" + matcher.group());
                }
                textParts.append(touch);
            } else if (singleMessage instanceof Image) {
                nonTextParts.append("[图片]");
            } else if (singleMessage instanceof At at) {
                String name = resolveAtName(bot, at.getTarget());
                nonTextParts.append("@").append(name).append(" ");
            } else if (singleMessage instanceof Face face) {
                nonTextParts.append("[表情:" + face.getName() + "]");
            }
            // QuoteReply / Audio / MusicShare / MarketFace 等对AI无意义，直接忽略
        }
        return textParts.toString() + nonTextParts.toString();
    }

    /**
     * 解析 @用户 的显示名称，优先取昵称，取不到则用ID
     */
    private static String resolveAtName(Bot bot, long targetId) {
        try {
            if (bot != null) {
                var friend = bot.getFriend(targetId);
                if (friend != null) {
                    return friend.getNick();
                }
            }
        } catch (Exception ignored) {
        }
        return String.valueOf(targetId);
    }
}
