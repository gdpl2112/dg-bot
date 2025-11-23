package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.kloping.date.FrameUtils;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.MusicKind;
import net.mamoe.mirai.message.data.MusicShare;
import net.mamoe.mirai.message.data.PlainText;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.github.gdpl2112.dg_bot.Utils.getRedirectUrl;

/**
 * @author github.kloping
 */
@Slf4j
@Component
public class SongPoint implements BaseOptional {
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
        if (out.startsWith("点歌")) {
            name = out.substring(2);
            type = TYPE_WY;
        } else if (out.startsWith("酷狗点歌")) {
            name = out.substring(4);
            type = TYPE_KUGOU;
        } else if (out.startsWith("网易点歌")) {
            name = out.substring(4);
            type = TYPE_WY;
        } else if (out.startsWith("QQ点歌")) {
            name = out.substring(4);
            type = TYPE_QQ;
        } else if (out.startsWith("抖音点歌")) {
            name = out.substring(4);
            type = TYPE_DY;
        } else if (out.startsWith("取消点歌") || out.startsWith("取消选择")) {
            SongData o = QID2DATA.remove(event.getSender().getId());
            event.getSubject().sendMessage("已取消.\n" + o.name);
        } else if (out.matches("\\d{1,2}")) {
            SongData e = QID2DATA.get(event.getSender().getId());
            if (e != null) {
                Integer n = Integer.valueOf(out);
                if (n == 0) {
                    String r = listSongs(event.getSender().getId(), e.type, e.p + 1, e.name);
                    if (r == null) event.getSubject().sendMessage("翻页时异常!");
                    else event.getSubject().sendMessage(r);
                } else {
                    Message msg = null;
                    try {
                        msg = pointSongs(e, n);
                    } catch (Exception ex) {
                        msg = new PlainText("选择时异常!\n" + ex.getMessage());
                    }
                    event.getSubject().sendMessage(msg);
                }
            }
        }
        if (name == null) return;
        else if (name.trim().isEmpty()) {
            event.getSubject().sendMessage("点歌功能:"
                    + "\n- 点歌[歌名]    #默认网易点歌"
                    + "\n- 网易点歌[歌名] #网易云音乐点歌"
                    + "\n- QQ点歌[歌名]  #QQ音乐点歌"
                    + "\n- 酷狗点歌[歌名] #酷狗音乐点歌"
                    + "\n- 抖音点歌[歌名] #抖音音乐点歌"
                    + "\n- 取消点歌   #点歌后取消选择"
                    + "\n\ntips: 点歌后选择对应数字即可!"
            );
        } else if (type != null) {
            String r = listSongs(event.getSender().getId(), type, 1, name);
            if (r == null) event.getSubject().sendMessage("搜索时异常!");
            else event.getSubject().sendMessage(r);
        }
    }


    public static final String TYPE_KUGOU = "kg";
    public static final String TYPE_QQ = "qq";
    public static final String TYPE_WY = "wy";
    public static final String TYPE_DY = "dy";

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


    public static final RestTemplate TEMPLATE = new RestTemplate();

    @NotNull
    public static Document getDocument(String url) {
        try {
            ResponseEntity<String> response = TEMPLATE.getForEntity(url, String.class);
            Document doc0 = Jsoup.parse(response.getBody());
            return doc0;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public abstract static class PointInterface {
        protected final String type;

        public PointInterface(String type) {
            this.type = type;
        }

        public String list0(Long qid, String name, Integer p) {
            try {
                return list(qid, name, p);
            } catch (Exception e) {
                log.error("point type({}) list error {}", type, e.getMessage(), e);
                return null;
            }
        }

        abstract Message point(Long qid, SongData data, Integer n);

        abstract String list(Long qid, String name, Integer p);
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

    public static final String SERVER_HOST = "https://kloping.top/";

    /**
     * 列出歌曲列表
     *
     * @param type
     * @param p    页数
     * @param name
     * @return
     */
    public static String listSongs(Long qid, String type, Integer p, String name) {
        PointInterface pi = POINT_INTERFACES.get(type);
        if (pi == null) {
            return "不支持的点歌类型";
        } else {
            return pi.list(qid, name, p);
        }
    }

    /**
     * 点出歌曲发送
     *
     * @param data
     * @param n    页数
     * @return
     */
    public static Message pointSongs(SongData data, Integer n) {
        PointInterface pi = POINT_INTERFACES.get(data.type);
        if (pi == null) {
            return new PlainText("不支持的点歌类型");
        } else {
            return pi.point(data.qid, data, n);
        }
    }
    public static Document getDocumentKugouList(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:145.0) Gecko/20100101 Firefox/145.0")
                    .header("Host", "mobilecdn.kugou.com")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .ignoreHttpErrors(true).timeout(5000).ignoreContentType(true).get();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    public static Document getDocumentKugouUrl(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:145.0) Gecko/20100101 Firefox/145.0")
                    .header("Host", "m.kugou.com")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .ignoreHttpErrors(true).timeout(5000).ignoreContentType(true).get();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static final Map<String, PointInterface> POINT_INTERFACES = Map.of(
            TYPE_QQ, new PointInterface(TYPE_QQ) {
                @Override
                String list(Long qid, String name, Integer p) {
                    Document doc0 = getDocument(String.format("https://zj.v.api.aa1.cn/api/qqmusic/demo.php?type=1&q=%s&p=%s&n=10", name, p));
                    JSONObject data = JSONObject.parseObject(doc0.body().text());
                    StringBuilder sb = new StringBuilder(String.format("歌名:%s,页数:%s,总数:%s\n", name, p, data.get("count")));
                    int n = 1;
                    for (Object o : data.getJSONArray("list")) {
                        JSONObject o1 = (JSONObject) o;
                        sb.append(n++ + ".").append(o1.getString("name")).append("--").append(o1.getString("singer")).append("\n");
                    }
                    sb.append("选择歌曲前数字.选择0时进入下一页");
                    QID2DATA.put(qid, new SongData(p, name, TYPE_QQ, doc0, qid, System.currentTimeMillis()));
                    return sb.toString() + "\n使用'取消点歌'/'取消选择'来取消选择";
                }

                @Override
                Message point(Long qid, SongData data, Integer n) {
                    Document doc0 = (Document) data.data;
                    String content = doc0.wholeText();
                    JSONObject jo = JSON.parseObject(content);
                    JSONArray arr0 = jo.getJSONArray("list");
                    jo = arr0.getJSONObject(n - 1);
                    String url = getRedirectUrl(jo.getString("url"));
                    MusicShare share = new MusicShare(MusicKind.QQMusic, jo.getString("name"), jo.getString("singer"), url, jo.getString("cover"), url);
                    return share;
                }
            }
            , TYPE_KUGOU, new PointInterface(TYPE_KUGOU) {

                @Override
                Message point(Long qid, SongData data, Integer n) {
                    JSONObject r0 = (JSONObject) data.data;
                    r0 = r0.getJSONObject("data");
                    JSONArray infos = r0.getJSONArray("info");
                    JSONObject info = infos.getJSONObject(n - 1);
                    Document doc0 = getDocumentKugouUrl("http://m.kugou.com/app/i/getSongInfo.php?hash="+info.getString("hash")+"&cmd=playInfo");
                    JSONObject out = JSON.parseObject(doc0.body().text());
                    String cover = out.getString("imgUrl");
                    String url = out.getString("url");
                    MusicShare share = new MusicShare(MusicKind.KugouMusic,
                            out.getString("songName"), out.getString("author_name"), url, cover, url);
                    return share;
                }

                @Override
                String list(Long qid, String name, Integer p) {
                    String url = String.format("http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=%s&page=%s&pagesize=%s", name, p, 10);
                    Document doc0 = getDocumentKugouList(url);
//                    Document doc0 = getDocument("https://api.yaohud.cn/api/music/migu?key=zn54xgS3NU0cOKEO0yQ&n=&msg=" + name);
                    String content = doc0.wholeText();
                    JSONObject obj = JSON.parseObject(content);
                    QID2DATA.put(qid, new SongData(p, name, type, obj, qid, System.currentTimeMillis()));
                    obj= obj.getJSONObject("data");
                    StringBuilder sb = new StringBuilder(String.format("歌名:%s,页数:%s,总数:%s\n", name, p, obj.getInteger("total")));
                    int n = 1;
                    JSONArray infos = obj.getJSONArray("info");
                    for (Object o : infos) {
                        JSONObject o1 = (JSONObject) o;
                        sb.append(n++ + ".").append(o1.getString("songname")).append("--").append(o1.getString("singername")).append("\n");
                    }
                    sb.append("选择歌曲前数字.选择0时进入下一页");
                    return sb.toString() + "\n使用'取消点歌'/'取消选择'来取消选择";
                }
            }
            , TYPE_WY, new PointInterface(TYPE_WY) {
                @Override
                Message point(Long qid, SongData data, Integer n) {
                    Document doc0 = (Document) data.data;
                    String content = doc0.wholeText();
                    JSONArray arr = JSON.parseArray(content);
                    JSONObject jo = arr.getJSONObject(n - 1);
                    String id = jo.getString("id");
                    String url = getRedirectUrl(SERVER_HOST + "api/music/get-url-by-id?id=" + id);
                    String cover = getRedirectUrl(SERVER_HOST + "api/music/get-cover-by-id?id=" + id);
                    MusicShare share = new MusicShare(
                            MusicKind.QQMusic, jo.getString("name"),
                            jo.getString("artist"), "https://music.163.com/#/song?id=" + id,
                            cover, url);
                    return share;
                }

                @Override
                String list(Long qid, String name, Integer p) {
                    Document doc0 = getDocument(SERVER_HOST + "api/music/search?keyword=" + name);
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
            }
            , TYPE_DY, new PointInterface(TYPE_DY) {

                @Override
                Message point(Long qid, SongData data, Integer n) {
                    Document doc0 = getDocument("https://api.cenguigui.cn/api/douyin/music/?msg="
                            + data.name + "&page=" + data.p + "&limit=10&type=json&n=" + n);
                    String content = doc0.wholeText();
                    JSONObject jo = JSON.parseObject(content);
                    JSONObject jd = jo.getJSONObject("data");
                    String url = jd.getString("url");
                    String cover = jd.getString("cover");
                    String title = jd.getString("title");
                    String singer = jd.getString("singer");
                    MusicShare share = new MusicShare(MusicKind.QQMusic, title, singer, url, cover, url);
                    return share;
                }

                @Override
                String list(Long qid, String name, Integer p) {
                    Document doc0 = getDocument("https://api.cenguigui.cn/api/douyin/music/?msg="
                            + name + "&page=" + p + "&limit=10&type=json&n=");
                    String content = doc0.wholeText();
                    StringBuilder sb = new StringBuilder();
                    JSONObject all = JSON.parseObject(content);
                    JSONArray arr = all.getJSONArray("data");
                    for (Object o : arr) {
                        JSONObject e0 = (JSONObject) o;
                        sb.append(e0.getInteger("n")).append(".")
                                .append(e0.getString("title")).append("--")
                                .append(e0.getString("singer")).append("\n");
                    }
                    QID2DATA.put(qid, new SongData(p, name, type, doc0, qid, System.currentTimeMillis()));
                    sb.append("选择歌曲前数字.选择0时进入下一页");
                    return sb.toString().trim() + "\n使用'取消点歌'/'取消选择'来取消选择";

                }
            }
    );

}
