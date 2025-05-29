package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.luciad.imageio.webp.WebPReadParam;
import io.github.kloping.judge.Judge;
import io.github.kloping.url.UrlUtils;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.ForwardMessageBuilder;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.MessageChainBuilder;
import net.mamoe.mirai.message.data.PlainText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
            if (matcher.find()) parseKs(matcher.group(), event);
        } else if (out.contains(DY_LINK)) {
            Matcher matcher = pattern.matcher(out);
            if (matcher.find()) parseDy(matcher.group(), event);
        }
    }

    public static final String regx = "(https?|http|ftp|file):\\/\\/[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]";
    public static final Pattern pattern = Pattern.compile(regx);

    public static final String KS_LINK = "v.kuaishou.com";
    public static final String DY_LINK = "v.douyin.com";


    public static final RestTemplate TEMPLATE = new RestTemplate();

    private void parseDy(final String url, MessageEvent event) {
        String out = TEMPLATE.getForObject("https://www.hhlqilongzhu.cn/api/sp_jx/sp.php?url=" + url, String.class);
        JSONObject result = JSON.parseObject(out);
        if (result.getInteger("code") < 0) {
            event.getSubject().sendMessage("解析异常!\n若链接无误请反馈.");
            return;
        }
        Gt gt = new Gt(out);

        Bot bot = event.getBot();

        var builder = new MessageChainBuilder();
        byte[] bytes = UrlUtils.getBytesFromHttpUrl(gt.gt("data.cover", String.class));
        Image image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(bytes), "jpg");
        builder.append(image)
                .append(gt.gt("data.title")).append("\n")
                .append(gt.gt("text.time")).append("\n")
                .append(gt.gt("text.msg"));
        String u0 = gt.gt("data.url", String.class);
        var fbuilder = new ForwardMessageBuilder(bot.getAsFriend());
        if (Judge.isEmpty(u0)) {
            fbuilder.add(bot.getId(), "AI", new PlainText("音频直链:" + gt.gt("data.music")));
            JSONArray array = gt.gt("data.images", JSONArray.class);
            builder.append("\n图集数量:").append(String.valueOf(array.size())).append("/正在发送请稍等..");
            event.getSubject().sendMessage(builder.build());
            for (Object o : array) {
                String url0 = o.toString();
                bytes = UrlUtils.getBytesFromHttpUrl(url0);
                if (url0.contains(".webp")) {
                    bytes = webp2png(bytes);
                }
                if (bytes == null) continue;
                image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(bytes), "png");
                fbuilder.add(bot.getId(), "AI", image);
            }
        } else {
            event.getSubject().sendMessage(builder.build());
            fbuilder.add(bot.getId(), "AI", new PlainText("视频直链: " + gt.gt("data.url")));
        }
        event.getSubject().sendMessage(fbuilder.build());
    }

    @Value("${opt.p1.host:localhost}")
    String host;

    public void parseKs(String url, MessageEvent event) {
        String out = TEMPLATE.getForObject("http://" + host + "/api/cre/jxvv?url=" + url, String.class);
        JSONObject result = JSON.parseObject(out);
        if (!result.containsKey("result")) {
            sendToAsVideo(event, result);
            return;
        }
        if (result.getInteger("result") < 0) {
            event.getSubject().sendMessage("解析异常!\n若链接无误请反馈.");
            return;
        }
        Gt gt = new Gt(out);

        Bot bot = event.getBot();

        var builder = new MessageChainBuilder();
        byte[] bytes = UrlUtils.getBytesFromHttpUrl(gt.gt("photo.coverUrls[0].url", String.class));
        Image image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(bytes), "jpg");
        builder.append(image)
                .append(gt.gt("photo.caption").toString())
                .append("作者").append(gt.gt("photo.userName")).append("/").append(gt.gt("photo.userSex"))
                .append("\n粉丝:").append(gt.gt("counts.fanCount"))
                .append("\n💗 ").append(gt.gt("photo.likeCount"))
                .append("\n👁︎︎ ").append(gt.gt("photo.viewCount"))
                .append("\n✉️ ").append(gt.gt("photo.commentCount"));

        ForwardMessageBuilder author = null;
        JSONArray array = gt.gt("shareUserPhotos", JSONArray.class);

        if (array != null && !array.isEmpty()) {
            author = new ForwardMessageBuilder(bot.getAsFriend());
            bytes = UrlUtils.getBytesFromHttpUrl(gt.gt("shareUserPhotos[0].headUrl", String.class));
            image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(bytes), "jpg");
            author.add(bot.getId(), "AI", image);
            author.add(bot.getId(), "AI", new PlainText("sharer," + gt.gt("shareUserPhotos[0].userName")
                    + "/" + gt.gt("shareUserPhotos[0].userSex")));
        }

        JSONObject atlas = gt.gt("atlas", JSONObject.class);

        if (atlas == null) {
            builder.append("\n视频时长:" + (gt.gt("photo.duration", Integer.class) / 1000) + "s");
            event.getSubject().sendMessage(builder.build());

            var de0 = new ForwardMessageBuilder(bot.getAsFriend());
            de0.add(bot.getId(), "AI", new PlainText("视频直链: " + gt.gt("photo.mainMvUrls[0].url")));
            de0.add(bot.getId(), "AI", new PlainText("音频直链: " + gt.gt("photo.soundTrack.audioUrls[0].url")));
            event.getSubject().sendMessage(de0.build());
        } else {
            builder.append("\n图集数量:" + gt.gt("atlas.list", JSONArray.class).size() + "/正在发送,请稍等...");
            event.getSubject().sendMessage(builder.build());

            var fbuilder = new ForwardMessageBuilder(bot.getAsFriend());
            if (author != null) fbuilder.add(bot.getId(), "AI", author.build());
            fbuilder.add(bot.getId(), "AI", new PlainText("音频直链: https://" + gt.gt("atlas.musicCdnList[0].cdn")
                    + gt.gt("atlas.music")));
            var arr = gt.gt("atlas.list", JSONArray.class);
            var host = "https://" + gt.gt("atlas.cdn[0]");
            for (var i = 0; i < arr.size(); i++) {
                var e = arr.get(i);
                try {
                    bytes = UrlUtils.getBytesFromHttpUrl(host + e);
                    image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(bytes), "jpg");
                    fbuilder.add(bot.getId(), "AI", image);
                } catch (Exception ex) {
                    fbuilder.add(bot.getId(), "AI", new PlainText("[图片加载失败;" + host + e + "]"));
                }
            }
            event.getSubject().sendMessage(fbuilder.build());
        }
    }


    private void sendToAsVideo(MessageEvent event, JSONObject result) {
        Bot bot = event.getBot();

        var builder = new MessageChainBuilder();
        byte[] bytes = UrlUtils.getBytesFromHttpUrl(result.getString("coverUrl"));
        Image image = Contact.uploadImage(event.getSubject(), new ByteArrayInputStream(bytes), "jpg");
        builder.append(image).append(result.getString("caption").toString()).append("\n💗 ").append(result.getString("likeCount")).append("\n👁︎︎ ").append(result.getString("viewCount"));
        builder.append("\n视频时长:" + (result.getInteger("duration") / 1000) + "s");
        event.getSubject().sendMessage(builder.build());

        var de0 = new ForwardMessageBuilder(bot.getAsFriend());
        de0.add(bot.getId(), "AI", new PlainText("视频直链: " + result.getString("photoUrl")));
        event.getSubject().sendMessage(de0.build());
    }


    public static class Gt {
        private String json;

        public Gt(String json) {
            this.json = json;
        }

        public String gt(String p) {
            return gt(json, p, Object.class).toString();
        }

        public <T> T gt(String p, Class<T> t) {
            return gt(json, p, t);
        }

        /**
         * get from json
         *
         * @param t1 json
         * @param t0 表达式
         * @return
         * @throws Exception
         */
        public static <T> T gt(String t1, String t0, Class<T> cla) {
            JSON j0 = (JSON) JSON.parse(t1);
            t0 = t0.trim();
            String s0 = null;
            for (String s : t0.trim().split("\\.")) {
                if (!s.isEmpty()) {
                    s0 = s;
                    break;
                }
            }
            Object o = null;
            if (s0.matches("\\[\\d*]")) {
                JSONArray arr = (JSONArray) j0;
                String sts = s0.substring(1, s0.length() - 1);
                if (sts.isEmpty()) {
                    o = arr;
                    t0 = t0.replaceFirst("\\[]", "");
                } else {
                    Integer st = Integer.parseInt(sts);
                    o = arr.get(st);
                    int len = 4;
                    if (t0.length() >= len) t0 = t0.substring(len);
                    else t0 = t0.substring(len - 1);
                }
            } else if (s0.matches(".*?\\[\\d+]")) {
                int i = s0.indexOf("[");
                int i1 = s0.indexOf("]");
                String st0 = s0.substring(0, i);
                Integer st = Integer.parseInt(s0.substring(i + 1, s0.length() - 1));
                JSONObject jo = (JSONObject) j0;
                o = jo.getJSONArray(st0).get(st);
                if (t0.length() > s0.length()) t0 = t0.substring(s0.length());
                else t0 = null;
            } else {
                JSONObject jo = (JSONObject) j0;
                o = jo.get(s0);
                int len = s0.length() + 1;
                if (t0.length() >= len) t0 = t0.substring(len);
                else t0 = t0.substring(len - 1);
            }
            if (t0 != null && t0.length() > 0) {
                return (T) gt(JSON.toJSONString(o), t0, cla);
            } else {
                return (T) o;
            }
        }
    }


    public static byte[] webp2png(byte[] webp) {
        ByteArrayOutputStream baos = null;
        try {
            // Obtain a WebP ImageReader instance
            ImageReader reader = ImageIO.getImageReadersByMIMEType("image/webp").next();

            // Configure decoding parameters
            WebPReadParam readParam = new WebPReadParam();
            readParam.setBypassFiltering(true);

            // Configure the input on the ImageReader
            reader.setInput(new MemoryCacheImageInputStream(new ByteArrayInputStream(webp)));

            // Decode the image
            BufferedImage image = reader.read(0, readParam);

            baos = new ByteArrayOutputStream();
            // the `png` can use `jpg`
            ImageIO.write(image, "png", baos);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return baos.toByteArray();
    }
}
