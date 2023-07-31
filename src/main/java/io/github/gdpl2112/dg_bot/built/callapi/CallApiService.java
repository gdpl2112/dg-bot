package io.github.gdpl2112.dg_bot.built.callapi;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.CallTemplate;
import io.github.gdpl2112.dg_bot.mapper.CallTemplateMapper;
import io.github.gdpl2112.dg_bot.service.PassiveService;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageSyncEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CallApiService extends SimpleListenerHost {

    @EventHandler
    public void onEvent(GroupMessageEvent event) {
        onEvent(event, "g" + event.getGroup().getId());
    }

    @EventHandler
    public void onEvent(GroupMessageSyncEvent event) {
        onEvent(event, "g" + event.getGroup().getId());
    }

    @EventHandler
    public void onEvent(FriendMessageEvent event) {
        onEvent(event, "f" + event.getFriend().getId());
    }

    @Autowired
    PassiveService passiveService;

    /**
     * step0
     *
     * @param event
     * @param tid
     */
    private void onEvent(MessageEvent event, String tid) {
        if (passiveService.isNotOpenK2(event.getBot().getId(), tid)) return;
        if (event.getMessage().size() > 1) {
            String text = toText(event.getMessage());
            Message message = call(text, event.getSubject().getId(), event.getSender().getId(), event.getBot());
            if (message != null) {
                event.getSubject().sendMessage(message);
            }
        }
    }

    private String toText(MessageChain m) {
        StringBuilder sb = new StringBuilder();
        for (SingleMessage singleMessage : m) {
            if (singleMessage instanceof PlainText) {
                sb.append(((PlainText) singleMessage).getContent());
            } else if (singleMessage instanceof At) {
                sb.append("[@").append(((At) singleMessage).getTarget()).append("]");
            } else if (singleMessage instanceof Image) {
                sb.append("[image:").append(Image.queryUrl((Image) singleMessage)).append("]");
            }
        }
        return sb.toString();
    }

    @Autowired
    CallTemplateMapper callTemplateMapper;

    /**
     * step1
     *
     * @param text
     * @param gid
     * @param qid
     * @param bot
     * @return
     */
    public Message call(String text, long gid, long qid, Bot bot) {
        try {
            String[] ss = text.split("\\s|,|，");
            if (ss == null || ss.length == 0) return null;
            String first = ss[0];
            QueryWrapper<CallTemplate> qw = new QueryWrapper<>();
            qw.eq("qid", bot.getId());
            qw.eq("touch", first);
            CallTemplate template = callTemplateMapper.selectOne(qw);
            if (template == null) {
                QueryWrapper<CallTemplate> qw1 = new QueryWrapper<>();
                qw1.eq("qid", bot.getId());
                for (CallTemplate callTemplate : callTemplateMapper.selectList(qw1)) {
                    if (text.startsWith(callTemplate.touch)) {
                        template = callTemplate;
                        ss = new String[]{template.touch, text.substring(template.touch.length())};
                        break;
                    }
                }
            }
            if (template == null) return null;
            String[] ss0 = new String[ss.length - 1];
            System.arraycopy(ss, 1, ss0, 0, ss0.length);
            //step in
            Connection connection = Worker.doc(bot, gid, qid, template, ss0);
            if (connection == null) return null;
            //step out
            return Worker.parse(connection, template, bot, gid, qid);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
