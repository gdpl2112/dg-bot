package io.github.gdpl2112.dg_bot.built.callapi;

import com.alibaba.fastjson.JSON;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.dao.CallTemplate;
import io.github.kloping.reg.MatcherUtils;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.PlainText;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;


/**
 * @author github.kloping
 */
public class Worker {

    public static Message work(Connection connection, CallTemplate template, Bot bot, long gid, long qid, Contact subject) {
        Message message = null;
        String out = template.out;
        try {
            int i = 1;
            AtomicReference<Document> doc0 = new AtomicReference<>();
            if (template.outArgs != null && !template.outArgs.isEmpty()) {
                for (String outArg : template.outArgs.split("\\s|,|，")) {
                    Object o0 = Converter.get(connection, outArg, doc0);
                    if (o0 != null) {
                        String o1 = o0.toString();
                        try {
                            JSON json = (JSON) JSON.parse(o1);
                        } catch (Exception e) {
                            o1 = o1.replaceAll(",", ";");
                        }
                        out = out.replaceFirst(String.format(Converter.CHAR0, i++), o1);
                    }
                }
            }
            out = Converter.filterArgs(out, bot, gid, qid);
            out = filterCall(out, template);
        } catch (Exception e) {
            if (e instanceof NullPointerException) {
                e.printStackTrace();
            }
            e.printStackTrace();
            out = "调用时失败";
        }
        try {
            message = DgSerializer.stringDeserializeToMessageChain(out, bot, subject);
        } catch (Exception e) {
            e.printStackTrace();
            out = "类型转换时失败";
            message = new PlainText(out);
        }
        return message;
    }

    //step 1
    public static Connection doc(Bot bot, long gid, long qid, CallTemplate template, String... args) throws Exception {
        int i = 1;
        String url = template.url;
        for (String arg : args) {
            url = url.replaceFirst(String.format(Converter.CHAR0, i++), arg);
        }
        url = Converter.filterArgs(url, bot, gid, qid, args);
        url = filterCall(url, template);
        try {
            return getConnection(url, template);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String filterCall(String url, CallTemplate template) throws Exception {
        for (String s : MatcherUtils.matcherAll(url, "\\$call.?(.+)")) {
            String ex = s.substring(s.indexOf("call") + 4, s.indexOf("("));
            String u0 = s.substring(s.indexOf("(") + 1, s.lastIndexOf(")"));
            String body = getConnection(u0, template).get().body().text();
            if (ex.isEmpty()) url = url.replace(s, body);
            else url = url.replace(s, Converter.getOutEnd(body, ex).toString());
        }
        return url;
    }

    public static Connection getConnection(String url, CallTemplate template) throws Exception {
        Connection connection = org.jsoup.Jsoup.connect(url).ignoreContentType(true).ignoreHttpErrors(true)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 Edg/114.0.1823.67");
        connection.timeout(60000);
        return connection;
    }
}
