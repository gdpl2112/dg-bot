package io.github.gdpl2112.dg_bot.service.optionals;

import io.github.kloping.judge.Judge;
import io.github.kloping.url.UrlUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.utils.ExternalResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author github.kloping
 */
@Component
public class ShortVideoParse implements BaseOptional {
    @Override
    public String getDesc() {
        return "自动检测消息中解析[快手/抖音][短视频/图集]短链接并解析发送结果";
    }

    @Override
    public String getName() {
        return "短视频自动解析";
    }

    @Override
    public void run(MessageEvent event) {
        String out = io.github.gdpl2112.dg_bot.Utils.getLineString(event);
        if (out.contains(KS_LINK)) {
            Matcher matcher = pattern.matcher(out);
            if (matcher.find()) parseNow(matcher.group(), event);
        } else if (out.contains(DY_LINK)) {
            Matcher matcher = pattern.matcher(out);
            if (matcher.find()) parseNow(matcher.group(), event);
        }
    }

    @Data
    @Accessors(chain = true)
    public static class JxData {
        private Integer code;
        private String msg;
        //douyin 或 kuaishou
        private String type;
        //image / video
        private String format;

        private String cover;
        private String title;
        private String author;

        private Object data;
    }

    public static final String regx = "(https?|http|ftp|file):\\/\\/[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]";
    public static final Pattern pattern = Pattern.compile(regx);

    public static final String KS_LINK = "v.kuaishou.com";
    public static final String DY_LINK = "v.douyin.com";


    public static final RestTemplate TEMPLATE = new RestTemplate();

    public void parseNow(String url, MessageEvent event) {
        System.out.println("开始解析: " + url);
        JxData jxData = TEMPLATE.getForObject("https://kloping.top/api/cre/jxvv?url=" + url, JxData.class);
        if (jxData == null || jxData.getCode() == null || jxData.getCode() != 200) {
            event.getSubject().sendMessage("解析异常!\n若链接无误请反馈.");
            System.err.println(jxData);
            return;
        }

        Bot bot = event.getBot();

        var builder = new MessageChainBuilder();

        byte[] coverBytes = null;
        if (Judge.isNotEmpty(jxData.getCover())) {
            coverBytes = UrlUtils.getBytesFromHttpUrl(jxData.getCover());
            Image image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(coverBytes), "jpg");
            builder.append(image);
        }

        builder.append("解析成功! 平台: ").append(jxData.getType()).append("\n");

        if (jxData.getTitle() != null)
            builder.append(jxData.getTitle());
        if (jxData.getAuthor() != null)
            builder.append(" \\ ").append(jxData.getAuthor());

        HashMap<String, Object> dataMap = (HashMap<String, Object>) jxData.getData();

        if (jxData.getFormat().equals("image")) {
            List<String> images = (List<String>) dataMap.get("images");
            builder.append("\n图片数量:" + images.size() + "/正在发送,请稍等...");
            event.getSubject().sendMessage(builder.build());

            var fbuilder = new ForwardMessageBuilder(event.getSubject());
            if (dataMap.containsKey("music"))
                fbuilder.add(bot, new PlainText("音频直链:" + dataMap.get("music")));
            for (String s : images) {
                try {
                    byte[] bytes = UrlUtils.getBytesFromHttpUrl(s);
                    Image image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(bytes), "jpg");
                    fbuilder.add(bot, image);
                } catch (Exception ex) {
                    fbuilder.add(bot, new PlainText("[图片加载失败;" + s + "]"));
                }
            }

            event.getSubject().sendMessage(fbuilder.build());
        } else {
            String u0 = dataMap.get("url").toString();
            byte[] bytes = UrlUtils.getBytesFromHttpUrl(u0);
            builder.append("\n视频大小:" + toView(bytes.length) + "\n正在发送,请稍等...: ");
            event.getSubject().sendMessage(builder.build());

            var fbuilder = new ForwardMessageBuilder(event.getSubject());
            if (coverBytes != null) {
                ShortVideo shortVideo = event.getSubject().uploadShortVideo(ExternalResource.create(coverBytes), ExternalResource.create(bytes),
                        jxData.getTitle() + ".mp4");
                fbuilder.add(bot, shortVideo);
            }

            fbuilder.add(bot, new PlainText("视频直链: " + u0));
            event.getSubject().sendMessage(fbuilder.build());
        }
    }

    private String toView(final int length) {
        int kb = 0;
        int mb = 0;
        int b = length;
        if (b >= 1024) {
            kb = b / 1024;
            b = b % 1024;
        }
        if (kb >= 1024) {
            mb = kb / 1024;
            kb = kb % 1024;
        }
        if (mb > 0) {
            double mb0 = mb + (kb / 1024.0);
            return String.format("%.2f MB", mb0);
        } else {
            double kb0 = kb + (b / 1024.0);
            return String.format("%.2f KB", kb0);
        }
    }
}
