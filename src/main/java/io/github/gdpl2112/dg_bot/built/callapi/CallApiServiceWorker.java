package io.github.gdpl2112.dg_bot.built.callapi;

import com.alibaba.fastjson.JSON;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.dao.CallTemplate;
import io.github.kloping.reg.MatcherUtils;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.PlainText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;


@Component
public class CallApiServiceWorker {

    public Message work(ConnectionContext connection, CallTemplate template, Bot bot, long gid, long qid, Contact subject) {
        Message message = null;
        String out = template.out;
        try {
            int i = 1;
            if (template.outArgs != null && !template.outArgs.isEmpty()) {
                for (String outArg : template.outArgs.split("\\s|,|，")) {
                    Object o0 = Converter.get(connection, outArg);
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
    public ConnectionContext doc(Bot bot, long gid, long qid, CallTemplate template, String text, String... args) throws Exception {
        int i = 1;
        String url = template.url;
        if (onlyOneArg(url)) {
            url = url.replaceAll("\\$1", text.substring(template.touch.length()).trim());
        } else {
            for (String arg : args) {
                url = url.replaceFirst(String.format(Converter.CHAR0, i++), arg);
            }
        }
        url = Converter.filterArgs(url, bot, gid, qid, args);
        url = filterCall(url, template);
        try {
            return getConnection(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean onlyOneArg(String url) {
        int i = 0;
        while (true) {
            i++;
            String f = String.format("$%s", i);
            if (url.contains(f)) continue;
            else break;
        }
        return i == 2;
    }

    public String filterCall(String url, CallTemplate template) throws Exception {
        for (String s : MatcherUtils.matcherAll(url, "\\$call.?(.+)")) {
            String ex = s.substring(s.indexOf("call") + 4, s.indexOf("("));
            String u0 = s.substring(s.indexOf("(") + 1, s.lastIndexOf(")"));
            String body = getConnection(u0).getBody();
            if (ex.isEmpty()) url = url.replace(s, body);
            else url = url.replace(s, Converter.getOutEnd(body, ex).toString());
        }
        return url;
    }

    @Autowired
    RestTemplate restTemplate;

    public ConnectionContext getConnection(String url) throws Exception {
        String body = restTemplate.getForObject(url, String.class);
        URI expanded = restTemplate.getUriTemplateHandler().expand(url);
        ConnectionContext context = new ConnectionContext();
        context.setUrl(expanded.toString());
        context.setBody(body);
        return context;
    }
}