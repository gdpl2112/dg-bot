package io.github.gdpl2112.dg_bot.service.optionals;

import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.PlainText;
import net.mamoe.mirai.message.data.SingleMessage;
import org.springframework.stereotype.Component;

import static io.github.gdpl2112.dg_bot.service.optionals.SongASyncMethod.*;

/**
 * @author github.kloping
 */
@Component
public class SongPoint extends BaseOptional   {
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

    @EventHandler
    public void pointOnly(GroupMessageEvent event) throws Exception {
        StringBuilder line = new StringBuilder();
        for (SingleMessage singleMessage : event.getMessage()) {
            if (singleMessage instanceof PlainText) {
                line.append(((PlainText) singleMessage).getContent().trim());
            }
        }
        String out = line.toString().trim();
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
}
