package io.github.gdpl2112.dg_bot.built.callapi;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.dao.CallTemplate;
import io.github.gdpl2112.dg_bot.mapper.CallTemplateMapper;
import io.github.gdpl2112.dg_bot.service.ConfigService;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageSyncEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.Message;
import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CallApiService extends SimpleListenerHost {

    @Autowired
    ConfigService configService;

    @Autowired
    CallTemplateMapper callTemplateMapper;

    @Autowired
    CallApiServiceWorker worker;

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

    /**
     * step0
     *
     * @param event
     * @param tid
     */
    private void onEvent(MessageEvent event, String tid) {
        if (configService.isNotOpenK0(event.getBot().getId(), tid)) return;
        if (event.getMessage().size() > 1) {
            String text = DgSerializer.messageChainSerializeToString(event.getMessage());
            Message message = call(text, event.getSubject().getId(), event.getSender().getId(), event.getBot(), event.getSubject());
            if (message != null) {
                event.getSubject().sendMessage(message);
            }
        }
    }

    /**
     * step1
     *
     * @param text
     * @param gid
     * @param qid
     * @param bot
     * @param subject
     * @return
     */
    public Message call(String text, long gid, long qid, Bot bot, Contact subject) {
        try {
            String[] oArgs = text.split("\\s|,|，");
            if (oArgs == null || oArgs.length == 0) return null;
            String first = oArgs[0];
            QueryWrapper<CallTemplate> qw = new QueryWrapper<>();
            qw.eq("qid", bot.getId());
            qw.eq("touch", first);
            CallTemplate template = callTemplateMapper.selectOne(qw);
            if (template == null) return null;
            String[] args = new String[oArgs.length - 1];
            System.arraycopy(oArgs, 1, args, 0, args.length);
            //step in
            ConnectionContext connection = worker.doc(bot, gid, qid, template, text, args);
            if (connection == null) return null;
            //step out
            return worker.work(connection, template, bot, gid, qid, subject);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
