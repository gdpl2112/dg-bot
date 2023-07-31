package io.github.gdpl2112.dg_bot.built.callapi;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.kloping.io.ReadUtils;
import io.github.kloping.number.NumberUtils;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.Member;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author github.kloping
 */
public class Converter {
    //输入参数
    public static final String QID = "$qid";
    public static final String QID0 = "\\$qid";

    public static final String QNAME = "$qname";
    public static final String QNAME0 = "\\$qname";

    public static final String MNAME = "$mname";
    public static final String MNAME0 = "\\$mname";

    public static final String GNAME = "$gname";
    public static final String GNAME0 = "\\$gname";

    public static final String GID = "$gid";
    public static final String GID0 = "\\$gid";

    public static final String CHAR0 = "\\$%s";

    public static final String PAR_NUMBER = "$number";
    public static final String PAR_NUMBER0 = "\\$number";

    public static final String PAR_URL0 = "\\$url";
    //输出参数
    public static final String PAR_URL = "$url";
    public static final String ALL = "$all";

    public static String getUrlStr(String data) {
        String regex = "(https?|ftp|file)://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]";
        Pattern p = Pattern.compile(regex);
        Matcher matcher = p.matcher(data);
        while (matcher.find()) {
            return matcher.group().trim();
        }
        return "";
    }

    /**
     * 从 接收的消息中分解参数 以填充完整的url
     *
     * @param url
     * @param bot
     * @param gid
     * @param qid
     * @param args
     * @return
     */
    public static String filterArgs(String url, Bot bot, long gid, long qid, String... args) {
        if (url == null) return url;
        if (url.contains(QID)) url = url.replaceAll(QID0, String.valueOf(qid));
        if (url.contains(GID)) url = url.replaceAll(GID0, String.valueOf(gid));
        if (url.contains(PAR_URL)) {
            String e1 = getUrlStr(Arrays.toString(args));
            if (e1.contains("&") || e1.contains("?") || e1.contains("=")) {
                e1 = URLEncoder.encode(e1);
            }
            url = url.replaceAll(PAR_URL0, e1);
        }
        if (url.contains(PAR_NUMBER)) {
            StringBuilder nums = new StringBuilder();
            for (String arg : args) {
                nums.append(NumberUtils.findNumberFromString(arg));
            }
            url = url.replaceAll(PAR_NUMBER0, String.valueOf(nums.toString()));
        }
        if (url.contains(QNAME)) {
            Friend friend = bot.getFriend(qid);
            if (friend != null) {
                url = url.replaceAll(QNAME0, friend.getNick());
            } else {
                Member member = bot.getGroup(gid).getMembers().get(qid);
                if (member != null)
                    url = url.replaceAll(QNAME0, member.getNick());
            }
        }
        if (url.contains(MNAME)) {
            Member member = bot.getGroup(gid).getMembers().get(qid);
            if (member != null)
                url = url.replaceAll(MNAME0, member.getNameCard());
        }
        if (url.contains(GNAME)) {
            Group group = bot.getGroup(gid);
            if (group != null)
                url = url.replaceAll(GNAME0, group.getName());
        }
        return url;
    }

    /**
     * 从获取到的数据 按指定方式转为数据类型
     *
     * @param t1
     * @param outArg
     * @param doc0
     * @return
     * @throws Exception
     */
    public static Object get(Connection t1, String outArg, AtomicReference<Document> doc0) throws Exception {
        if (outArg.equals(ALL)) return ReadUtils.readAll(t1.execute().bodyStream(), "utf-8");
        if (outArg.equals(PAR_URL)) return t1.get().location();
        if (doc0.get() == null) {
            doc0.set(t1.get());
        }
        return getOutEnd(doc0.get().body().text(), outArg);
    }

    /**
     * get from json
     *
     * @param json json
     * @param arg  表达式
     * @return
     * @throws Exception
     */
    public static Object getOutEnd(String json, String arg) {
        try {
            JSON j0 = (JSON) JSON.parse(json);
            arg = arg.trim();
            String s0 = arg.trim().split("\\.")[0].trim();
            Object o = null;
            if (s0.matches("\\[\\d*]")) {
                JSONArray arr = (JSONArray) j0;
                String sts = s0.substring(1, s0.length() - 1);
                if (sts.isEmpty()) {
                    o = arr;
                    arg = arg.replaceFirst("\\[]", "");
                } else {
                    Integer st = Integer.parseInt(sts);
                    o = arr.get(st);
                    int len = 4;
                    if (arg.length() >= len) arg = arg.substring(len);
                    else arg = arg.substring(len - 1);
                }
            } else if (s0.matches(".*?\\[\\d+]")) {
                int i = s0.indexOf("[");
                int i1 = s0.indexOf("]");
                String st0 = s0.substring(0, i);
                Integer st = Integer.parseInt(s0.substring(i + 1, s0.length() - 1));
                JSONObject jo = (JSONObject) j0;
                o = jo.getJSONArray(st0).get(st);
                int len = 4 + st0.length();
                if (arg.length() >= len) arg = arg.substring(len);
                else arg = arg.substring(len - 1);
            } else {
                JSONObject jo = (JSONObject) j0;
                o = jo.get(s0);
                int len = s0.length() + 1;
                if (arg.length() >= len) arg = arg.substring(len);
                else arg = arg.substring(len - 1);
            }
            if (arg.length() > 0) {
                return getOutEnd(JSON.toJSONString(o), arg);
            } else {
                return o;
            }
        } catch (com.alibaba.fastjson.JSONException jex) {
            return arg;
        } catch (Exception ex) {
            ex.printStackTrace();
            return arg;
        }
    }
}
