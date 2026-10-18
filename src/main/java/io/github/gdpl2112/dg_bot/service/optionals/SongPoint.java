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
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    public static final String TYPE_KUGOU = "kg";
    public static final String TYPE_QQ = "qq";
    public static final String TYPE_WY = "wy";
    public static final String TYPE_DY = "dy";
    /**
     * 待选择歌曲的临时缓存：QQ号 -> 本次列表快照。
     * 使用 {@link ConcurrentHashMap} 是因为写入发生在消息处理线程，
     * 读取/清理同时发生在定时任务线程，普通 HashMap 会有并发安全问题。
     */
    public static final Map<Long, SongData> QID2DATA = new ConcurrentHashMap<>();
    /**
     * 选择窗口时长：点歌列表下发后，30 分钟内未选择则自动取消。
     */
    public static final long SELECT_WINDOW_MILLIS = 30L * 60L * 1000L;
    /**
     * @deprecated 语义等同于 {@link #SELECT_WINDOW_MILLIS}，保留仅为兼容旧引用。
     */
    @Deprecated
    public static final long MAX_CD = SELECT_WINDOW_MILLIS;
    /**
     * 列表消息尾部的统一提示语，避免四处硬编码提示文案与有效期不一致。
     */
    public static final String SELECT_TIP = "\n使用'取消点歌'/'取消选择'来取消选择"
            + "\n选择有效期30分钟，超时自动取消";
    public static final RestTemplate TEMPLATE = new RestTemplate();
    public static final String SERVER_HOST = "https://kloping.top/";
    private static final Map<String, PointInterface> POINT_INTERFACES = Map.of(
            TYPE_QQ, new PointInterface(TYPE_QQ) {
                @Override
                String list(Long qid, String name, Integer p) {
                    String content = getString(String.format("https://zj.v.api.aa1.cn/api/qqmusic/demo.php?type=1&q=%s&p=%s&n=10", name, p));
                    JSONObject data = JSONObject.parseObject(content);
                    StringBuilder sb = new StringBuilder(String.format("歌名:%s,页数:%s,总数:%s\n", name, p, data.get("count")));
                    int n = 1;
                    for (Object o : data.getJSONArray("list")) {
                        JSONObject o1 = (JSONObject) o;
                        sb.append(n++ + ".").append(o1.getString("name")).append("--").append(o1.getString("singer")).append("\n");
                    }
                    sb.append("选择歌曲前数字.选择0时进入下一页");
                    QID2DATA.put(qid, new SongData(p, name, TYPE_QQ, content, qid, System.currentTimeMillis()));
                    return sb.toString() + SELECT_TIP;
                }

                @Override
                Message point(Long qid, SongData data, Integer n) {
                    String content = (String) data.data;
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
                    String content = getStringKugouUrl("http://m.kugou.com/app/i/getSongInfo.php?hash=" + info.getString("hash") + "&cmd=playInfo");
                    JSONObject out = JSON.parseObject(content);
                    String cover = out.getString("imgUrl");
                    String url = out.getString("url");
                    MusicShare share = new MusicShare(MusicKind.KugouMusic,
                            out.getString("songName"), out.getString("author_name"), url, cover, url);
                    return share;
                }

                @Override
                String list(Long qid, String name, Integer p) {
                    String url = String.format("http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=%s&page=%s&pagesize=%s", name, p, 10);
                    String content = getStringKugouList(url);
//                    String content = getString("https://api.yaohud.cn/api/music/migu?key=zn54xgS3NU0cOKEO0yQ&n=&msg=" + name);
                    JSONObject obj = JSON.parseObject(content);
                    QID2DATA.put(qid, new SongData(p, name, type, obj, qid, System.currentTimeMillis()));
                    obj = obj.getJSONObject("data");
                    StringBuilder sb = new StringBuilder(String.format("歌名:%s,页数:%s,总数:%s\n", name, p, obj.getInteger("total")));
                    int n = 1;
                    JSONArray infos = obj.getJSONArray("info");
                    for (Object o : infos) {
                        JSONObject o1 = (JSONObject) o;
                        sb.append(n++ + ".").append(o1.getString("songname")).append("--").append(o1.getString("singername")).append("\n");
                    }
                    sb.append("选择歌曲前数字.选择0时进入下一页");
                    return sb.toString() + SELECT_TIP;
                }
            }
            , TYPE_WY, new PointInterface(TYPE_WY) {
                @Override
                Message point(Long qid, SongData data, Integer n) {
                    String content = (String) data.data;
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
                    String content = getString(SERVER_HOST + "api/music/search?keyword=" + name);
                    StringBuilder sb = new StringBuilder();
                    JSONArray arr = JSON.parseArray(content);
                    Integer index = 1;
                    for (Object o : arr) {
                        JSONObject e0 = (JSONObject) o;
                        sb.append(index++).append(".").append(e0.getString("name")).append("--").append(e0.getString("artist")).append("\n");
                    }
                    QID2DATA.put(qid, new SongData(p, name, type, content, qid, System.currentTimeMillis()));
                    return sb.toString().trim() + SELECT_TIP;
                }
            }
            , TYPE_DY, new PointInterface(TYPE_DY) {

                @Override
                Message point(Long qid, SongData data, Integer n) {
                    String content = getString("https://api.cenguigui.cn/api/douyin/music/?msg="
                            + data.name + "&page=" + data.p + "&limit=10&type=json&n=" + n);
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
                    String content = getString("https://api.cenguigui.cn/api/douyin/music/?msg="
                            + name + "&page=" + p + "&limit=10&type=json&n=");
                    StringBuilder sb = new StringBuilder();
                    JSONObject all = JSON.parseObject(content);
                    JSONArray arr = all.getJSONArray("data");
                    for (Object o : arr) {
                        JSONObject e0 = (JSONObject) o;
                        sb.append(e0.getInteger("n")).append(".")
                                .append(e0.getString("title")).append("--")
                                .append(e0.getString("singer")).append("\n");
                    }
                    QID2DATA.put(qid, new SongData(p, name, type, content, qid, System.currentTimeMillis()));
                    sb.append("选择歌曲前数字.选择0时进入下一页");
                    return sb.toString().trim() + SELECT_TIP;

                }
            }
    );

    /**
     * 过期记录清理任务。
     * <p>
     * 职责：周期性扫描 {@link #QID2DATA}，移除超过 {@link #SELECT_WINDOW_MILLIS} 的选择记录，
     * 防止长期驻留造成内存泄漏。<b>此清理过程完全静默</b>，不向群聊或私聊发送任何提示，
     * 用户侧只表现为"再发数字不再有响应"。
     * <p>
     * 扫描周期取 30 秒：由于选择动作在 {@link #getValidSelection(Long)} 中会再做一次时效校验，
     * 这里的周期只影响内存回收的及时性，不影响业务上的超时判定精度。
     */
    static {
        FrameUtils.SERVICE.scheduleWithFixedDelay(() -> {
            try {
                long now = System.currentTimeMillis();
                // ConcurrentHashMap 的 entrySet().removeIf 是并发安全的，不会像 HashMap 迭代器那样抛 ConcurrentModificationException
                QID2DATA.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
            } catch (Exception e) {
                log.error("song selection expire scan error: {}", e.getMessage(), e);
            }
        }, 1, 30, TimeUnit.SECONDS);
    }

    /**
     * 判断某条选择记录是否已超出 30 分钟选择窗口。
     *
     * @param data 选择记录，允许为 null
     * @param now  当前时间戳（毫秒）
     * @return true 表示已过期（或记录为空）
     */
    public static boolean isExpired(SongData data, long now) {
        return data == null || data.time == null || (now - data.time) > SELECT_WINDOW_MILLIS;
    }

    /**
     * 取出该 QQ 号当前有效的选择记录。
     * <p>
     * 超时记录会在此处被<b>静默移除</b>并返回 null：不做任何消息回复，
     * 以此保证"超时自动取消点歌、无通知"的用户体验。
     *
     * @param qid QQ号
     * @return 有效记录；未点歌或已超时返回 null
     */
    public static SongData getValidSelection(Long qid) {
        if (qid == null) {
            return null;
        }
        SongData data = QID2DATA.get(qid);
        if (data == null) {
            return null;
        }
        if (isExpired(data, System.currentTimeMillis())) {
            QID2DATA.remove(qid, data);
            return null;
        }
        return data;
    }

    /**
     * 获取指定URL的内容
     *
     * @param url 目标URL
     * @return 返回响应内容字符串，失败则返回null
     */
    public static String getString(String url) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build();
        try (Response response = io.github.gdpl2112.dg_bot.Utils.OK_HTTP_CLIENT.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            return null;
        } catch (Exception e) {
            log.error("getString error {}", e.getMessage(), e);
            return null;
        }
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

    /**
     * 程序化搜索并直接获取指定排名歌曲的音乐卡片消息
     * <p>
     * 不污染用户级 QID2DATA 缓存，调用结束后会清理临时数据
     *
     * @param type 平台类型: wy/qq/kg/dy
     * @param name 歌曲名称
     * @param n    选择第几首（从 1 开始，非法值会被回退为 1）
     * @return 音乐卡片消息，异常或无结果时返回带提示的 PlainText
     */
    public static Message pickAsCard(String type, String name, Integer n) {
        if (type == null || name == null || name.trim().isEmpty()) {
            return new PlainText("点歌参数不能为空");
        }
        PointInterface pi = POINT_INTERFACES.get(type);
        if (pi == null) {
            return new PlainText("不支持的点歌类型: " + type);
        }
        int index = (n == null || n <= 0) ? 1 : n;
        // 使用唯一负数 qid 作为临时键，避免与真实 QQ 号冲突
        Long tempQid = -System.nanoTime();
        try {
            String listResult = pi.list0(tempQid, name.trim(), 1);
            if (listResult == null) {
                return new PlainText("音乐搜索失败");
            }
            SongData data = QID2DATA.get(tempQid);
            if (data == null) {
                return new PlainText("音乐搜索结果为空");
            }
            return pi.point(tempQid, data, index);
        } catch (Exception e) {
            log.error("pickAsCard error type={}, name={}", type, name, e);
            return new PlainText("点歌失败: " + e.getMessage());
        } finally {
            QID2DATA.remove(tempQid);
        }
    }

    /**
     * 获取酷狗音乐列表页面
     *
     * @param url 目标URL
     * @return 响应内容字符串
     */
    public static String getStringKugouList(String url) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:145.0) Gecko/20100101 Firefox/145.0")
                .header("Host", "mobilecdn.kugou.com")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .build();
        try (Response response = io.github.gdpl2112.dg_bot.Utils.OK_HTTP_CLIENT.newCall(request).execute()) {
            if (response.body() != null) {
                return response.body().string();
            }
            return null;
        } catch (IOException e) {
            log.error("kugou request error", e);
            return null;
        }
    }

    /**
     * 获取酷狗音乐详情页面
     *
     * @param url 目标URL
     * @return 响应内容字符串
     */
    public static String getStringKugouUrl(String url) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:145.0) Gecko/20100101 Firefox/145.0")
                .header("Host", "m.kugou.com")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .build();
        try (Response response = io.github.gdpl2112.dg_bot.Utils.OK_HTTP_CLIENT.newCall(request).execute()) {
            if (response.body() != null) {
                return response.body().string();
            }
            return null;
        } catch (IOException e) {
            log.error("kugou request error", e);
            return null;
        }
    }

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
            // 主动取消属于显式指令，需要回执；与"超时自动取消"的静默行为区分开
            SongData o = QID2DATA.remove(event.getSender().getId());
            if (o == null) {
                event.getSubject().sendMessage("当前没有待选择的点歌");
            } else {
                event.getSubject().sendMessage("已取消.\n" + o.name);
            }
        } else if (out.matches("\\d{1,2}")) {
            // 统一做时效校验：未点歌或已超过 30 分钟选择窗口时返回 null，
            // 此分支不发送任何消息，实现"超时自动取消点歌、静默无通知"
            SongData e = getValidSelection(event.getSender().getId());
            if (e != null) {
                Integer n = Integer.valueOf(out);
                if (n == 0) {
                    // 记录原始计时起点：翻页是同一轮点歌的延续，不重新计算 30 分钟，避免无限翻页续期
                    long startedAt = e.time;
                    String r = listSongs(event.getSender().getId(), e.type, e.p + 1, e.name);
                    if (r == null) event.getSubject().sendMessage("翻页时异常!");
                    else {
                        SongData renewed = QID2DATA.get(event.getSender().getId());
                        if (renewed != null) renewed.time = startedAt;
                        event.getSubject().sendMessage(r);
                    }
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
                    + "\n\ntips: 点歌后选择对应数字即可! 选择有效期30分钟，超时自动取消。"
            );
        } else if (type != null) {
            String r = listSongs(event.getSender().getId(), type, 1, name);
            if (r == null) event.getSubject().sendMessage("搜索时异常!");
            else event.getSubject().sendMessage(r);
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
        public Integer p = 1;
        public String name;
        public String type;
        public Object data;
        public Long qid;
        /**
         * 本轮点歌的计时起点（毫秒时间戳）。
         * 声明为 volatile：翻页时会在消息线程回写该值以保持计时起点不变，
         * 定时清理线程需要立即读到最新值。
         */
        public volatile Long time;
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
    }

}
