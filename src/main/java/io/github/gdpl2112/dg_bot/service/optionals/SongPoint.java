package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.kloping.date.FrameUtils;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author github.kloping
 */
@Component
public class SongPoint implements BaseOptional   {
    public static final String DESC = "包含[点歌,QQ点歌,酷狗点歌,网易点歌,取消点歌,取消选择] 功能";
    public static final String NAME = "异步点歌";

    @Override
    public String getDesc() {
        return DESC;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void run(MessageEvent event) {
        String out = io.github.gdpl2112.dg_bot.Utils.getLineString(event);
        String name = null;
        String type = null;
        if (out.startsWith("酷狗点歌") && out.length() > 4) {
            name = out.substring(4);
            type = TYPE_KUGOU;
        } else if (out.startsWith("网易点歌") && out.length() > 4) {
            name = out.substring(4);
            type = TYPE_WY;
        } else if (out.startsWith("点歌") && out.length() > 2) {
            name = out.substring(2);
            type = TYPE_QQ;
        } else if (out.startsWith("QQ点歌") && out.length() > 4) {
            name = out.substring(4);
            type = TYPE_QQ;
        } else if (out.startsWith("取消点歌") || out.startsWith("取消选择")) {
            SongData o = QID2DATA.remove(event.getSender().getId());
            event.getSubject().sendMessage("已取消.\n" + o.name);
        } else if (out.matches("[+\\-\\d]+")) {
            Integer n = Integer.valueOf(out);
            SongData e = QID2DATA.get(event.getSender().getId());
            if (e != null) {
                if (n == 0) {
                    String r = listSongs(event.getSender().getId(), e.type, e.p + 1, e.name);
                    if (r == null) event.getSubject().sendMessage("翻页时异常!");
                    else event.getSubject().sendMessage(r);
                } else {
                    Message msg = pointSongs(e, n);
                    if (msg == null) event.getSubject().sendMessage("选择时异常!");
                    else event.getSubject().sendMessage(msg);
                }
            }
        }

        if (name != null && type != null) {
            String r = listSongs(event.getSender().getId(), type, 1, name);
            if (r == null) event.getSubject().sendMessage("点歌时异常!");
            else event.getSubject().sendMessage(r);
        }
    }


    public static final RestTemplate TEMPLATE = new RestTemplate();

    public static final String TYPE_KUGOU = "KG";
    public static final String TYPE_QQ = "qq";
    public static final String TYPE_WY = "wy";

    // id <type,data>
    public static final Map<Long, SongData> QID2DATA = new HashMap<>();

    public static final long MAX_CD = 1000 * 60 * 30;

    static {
        FrameUtils.SERVICE.scheduleWithFixedDelay(() -> {
            Iterator<Long> iterator = QID2DATA.keySet().iterator();
            while (iterator.hasNext()) {
                Long qid = iterator.next();
                SongData data = QID2DATA.get(qid);
                if ((System.currentTimeMillis() - data.time) > MAX_CD) {
                    QID2DATA.remove(qid);
                }
            }
        }, 20, 30, TimeUnit.MINUTES);
    }

    public static void qqvip(GroupMessageEvent event, String name, Integer p) throws Exception {
        Document doc0 = getDocument(String.format("https://api.linhun.vip/api/qqyy?&apiKey=5ff26395f76d3e12b694e1875e37a40a&y=1&n=&name=%s", name, p));
        JSONObject jo0 = JSON.parseObject(doc0.body().text());
        QID2DATA.put(event.getSender().getId(), new SongData(p, name, TYPE_QQ, jo0, event.getSender().getId(), System.currentTimeMillis()));
        if (jo0.getInteger("code") == 200) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Object o1 : jo0.getJSONArray("data")) {
                JSONObject e0 = (JSONObject) o1;
                sb.append(i++).append(".").append(e0.getString("name")).append("--").append(e0.getString("singer")).append("\n");
            }
            sb.append(jo0.getString("msg")).append("\ntips:选择'0'可翻向下一页\n使用'取消点歌'/'取消选择'来取消选择");
            event.getSubject().sendMessage(sb.toString());
        } else {
            event.getSubject().sendMessage(jo0.getString("msg") + "\n使用'取消点歌'/'取消选择'来取消选择");
        }
        return;
    }

    public static void kugouVip(GroupMessageEvent event, String name, Integer p) throws Exception {
        Document doc0 = getDocument("http://www.dreamling.top/API/kugou/web/music/api.php?&pagenum=9&format=json&flag=format&page=" + p + "&keyword=" + name);
        JSONObject jo0 = JSON.parseObject(doc0.body().text());
        QID2DATA.put(event.getSender().getId(), new SongData(p, name, TYPE_KUGOU, jo0, event.getSender().getId(), System.currentTimeMillis()));
        if (jo0.getInteger("code") == 200) {
            JSONObject data = jo0.getJSONObject("data");
            StringBuilder sb = new StringBuilder();
            sb.append("共搜索到'").append(data.getInteger("total")).append("'个结果").append(";当前第'").append(data.getInteger("page")).append("'页\n");
            int i = 1;
            for (Object o1 : data.getJSONArray("name")) {
                JSONObject e0 = (JSONObject) o1;
                sb.append(i++).append(".").append(e0.getString("Name")).append("\n");
            }
            sb.append("tips:选择'0'可翻向下一页\n使用'取消点歌'/'取消选择'来取消选择");
            event.getSubject().sendMessage(sb.toString());
        } else {
            event.getSubject().sendMessage(jo0.getString("搜索异常啦o(╥﹏╥)o"));
        }
        return;
    }

    @NotNull
    public static Document getDocument(String url) {
        try {
            Document doc0 = Jsoup.connect(url).ignoreHttpErrors(true).ignoreContentType(true).header("Connection", "Keep-Alive").header("User-Agent", "Apache-HttpClient/4.5.14 (Java/17.0.8.1)").header("Accept-Encoding", "br,deflate,gzip,x-gzip").get();
            return doc0;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取重定向地址
     *
     * @param path
     * @return
     * @throws Exception
     */
    public static String getRedirectUrl(String path) throws Exception {
        Document doc0 = getDocument(path);
        return doc0.location();
    }

    public static class SongData {
        public SongData(String type, String name, Long qid, Long time) {
            this.type = type;
            this.name = name;
            this.data = name;
            this.qid = qid;
            this.time = time;
        }

        public SongData(Integer p, String name, String type, Object data, Long qid, Long time) {
            this.p = p;
            this.name = name;
            this.type = type;
            this.data = data;
            this.qid = qid;
            this.time = time;
        }

        public Integer p = 1;
        public String name;
        public String type;
        public Object data;
        public Long qid;
        public Long time;
    }

    /**
     * 列出歌曲列表
     *
     * @param type
     * @param p    页数
     * @param name
     * @return
     */
    public static String listSongs(Long qid, String type, Integer p, String name) {
        try {
            if (TYPE_QQ.equals(type)) return listQqSongs(qid, TYPE_QQ, p, name);
            else if (TYPE_WY.equals(type)) return listWySongs(qid, TYPE_WY, p, name);
            else if (TYPE_KUGOU.equals(type)) return listKgSongs(qid, TYPE_KUGOU, p, name);
            else return "未知歌曲类型";
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private static String listKgSongs(Long qid, String type, Integer p, String name) throws Exception {
        Document doc0 = getDocument("https://www.hhlqilongzhu.cn/api/dg_kgmusic.php?n=&gm=" + name);
        QID2DATA.put(qid, new SongData(p, name, type, doc0, qid, System.currentTimeMillis()));
        return doc0.wholeText() + "\n使用'取消点歌'/'取消选择'来取消选择";
    }

    private static String listWySongs(Long qid, String type, Integer p, String name) throws Exception {
        Document doc0 = getDocument("http://127.0.0.1/api/music/search?keyword=" + name);
        String content = doc0.wholeText();
        StringBuilder sb = new StringBuilder();
        JSONArray arr = JSON.parseArray(content);
        Integer index = 1;
        for (Object o : arr) {
            JSONObject e0 = (JSONObject) o;
            sb.append(index++).append(".").append(e0.getString("name")).append("--").append(e0.getString("artist")).append("\n");
        }
        QID2DATA.put(qid, new SongData(p, name, type, doc0, qid, System.currentTimeMillis()));
        return sb.toString().trim() + "\n使用'取消点歌'/'取消选择'来取消选择";
    }

    private static String listQqSongs(Long qid, String type, Integer p, String name) throws Exception {
        Document doc0 = getDocument(String.format("https://www.hhlqilongzhu.cn/api/dg_qqmusic_SQ.php?type=text&br=2&msg=%s&n=", name));
//        JSONObject data = JSONObject.parseObject(doc0.body().text());
//        StringBuilder sb = new StringBuilder(String.format("歌名:%s,页数:%s,总数:%s\n", name, p, data.get("count")));
//        int n = 1;
//        for (Object o : data.getJSONArray("data")) {
//            JSONObject o1 = (JSONObject) o;
//            sb.append(n++ + ".").append(o1.getString("song")).append("--").append(o1.getString("singer")).append("\n");
//        }
//        sb.append("选择歌曲前数字.选择0时进入下一页");
        QID2DATA.put(qid, new SongData(p, name, TYPE_QQ, doc0, qid, System.currentTimeMillis()));
        return doc0.wholeText() + "\n使用'取消点歌'/'取消选择'来取消选择";
    }

    /**
     * 点出歌曲发送
     *
     * @param data
     * @param n    页数
     * @return
     */
    public static Message pointSongs(SongData data, Integer n) {
        try {
            if (TYPE_QQ.equals(data.type)) return pointQqSong(data, n);
            else if (TYPE_WY.equals(data.type)) return pointWySong(data, n);
            else if (TYPE_KUGOU.equals(data.type)) return pointKgSong(data, n);
            else return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Message pointKgSong(SongData e, Integer n) throws Exception {
        Document doc0 = getDocument("https://www.hhlqilongzhu.cn/api/dg_kgmusic.php?type=json&n=" + n + "&gm=" + e.name);
        JSONObject out = JSON.parseObject(doc0.body().text());
        String url = out.getString("music_url");
        MusicShare share = new MusicShare(MusicKind.QQMusic, out.getString("title"), out.getString("singer"), url, out.getString("cover"), url);
        return share;
    }

    private static Message pointWySong(SongData data, Integer n) throws Exception {
        Document doc0 = (Document) data.data;
        String content = doc0.wholeText();
        JSONArray arr = JSON.parseArray(content);
        JSONObject jo = arr.getJSONObject(n - 1);
        String id = jo.getString("id");
        String url = getRedirectUrl("http://127.0.0.1/api/music/get-url-by-id?id=" + id);
        String cover = getRedirectUrl("http://127.0.0.1/api/music/get-cover-by-id?id=" + id);
        MusicShare share = new MusicShare(
                MusicKind.QQMusic, jo.getString("name"),
                jo.getString("artist"), "https://music.163.com/#/song?id=" + id,
                cover, url
        );
        return share;
    }

    private static MusicShare pointQqSong(SongData data, Integer n) throws Exception {
        String jsonData = TEMPLATE.getForObject(String.format("https://www.hhlqilongzhu.cn/api/dg_qqmusic_SQ.php?type=json&br=2&msg=%s&n=%s", data.name, n), String.class);
        JSONObject jo = JSON.parseObject(jsonData);
        jo = jo.getJSONObject("data");
        MusicShare share = new MusicShare(
                MusicKind.QQMusic, jo.getString("song_name"),
                jo.getString("song_singer"), jo.getString("music_url"),
                jo.getString("cover"), jo.getString("music_url"));
        return share;
    }
}
