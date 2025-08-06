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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public Message call(final String text, long gid, long qid, Bot bot, Contact subject) {
        try {
            String[] oArgs = text.split("[\\s,，]{1,}");
            if (oArgs == null || oArgs.length == 0) return null;
            String touch = oArgs[0];
            String[] args = new String[oArgs.length - 1];
            System.arraycopy(oArgs, 1, args, 0, args.length);
            QueryWrapper<CallTemplate> qw = new QueryWrapper<>();
            qw.eq("qid", bot.getId());
            qw.eq("touch", touch);
            CallTemplate template = callTemplateMapper.selectOne(qw);
            if (template == null) {
                Map<String, CallTemplate> templates = cache.get(qid);
                if (templates == null) cache.put(qid, templates = new HashMap<>());
                if (templates.isEmpty()) {
                    qw.clear();
                    qw.eq("qid", bot.getId());
                    List<CallTemplate> callTemplates = callTemplateMapper.selectList(qw);
                    for (CallTemplate callTemplate : callTemplates) {
                        templates.put(callTemplate.getTouch(), callTemplate);
                    }
                }
                for (String key : templates.keySet()) {
                    if (touch.startsWith(key)) {
                        if (template == null || template.getTouch().length() < key.length()) {
                            template = templates.get(key);
                            args = text.substring(key.length()).split("[\\s,，]{1,}");
                        }
                    }
                }
            }
            if (template == null) return null;
            ConnectionContext connection = worker.doc(bot, gid, qid, template, text, args);
            if (connection == null) return null;
            return worker.work(connection, template, bot, gid, qid, subject);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Map<Long, Map<String, CallTemplate>> cache = new HashMap<>();

    public void clear(String qid) {
        Long id = Long.parseLong(qid);
        Map map = cache.remove(id);
        if (map != null) map.clear();
    }
}
