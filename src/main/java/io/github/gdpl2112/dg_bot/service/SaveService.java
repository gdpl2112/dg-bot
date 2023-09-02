package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.AllMessage;
import io.github.gdpl2112.dg_bot.dao.GroupConf;
import io.github.gdpl2112.dg_bot.mapper.GroupConfMapper;
import io.github.gdpl2112.dg_bot.mapper.SaveMapper;
import kotlin.coroutines.CoroutineContext;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.data.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 独立
 *
 * @author github-kloping
 * @version 1.0
 */
@Service
public class SaveService extends SimpleListenerHost {
    @Autowired
    SaveMapper saveMapper;

    public void save(AllMessage msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return;
        }
        saveMapper.insert(msg);
    }

    public SaveService(String[] args) {
    }

    @Override
    public void handleException(@NotNull CoroutineContext context, @NotNull Throwable exception) {

    }

    @EventHandler
    public void onMessage(@NotNull GroupMessageEvent event) throws Exception {
        save(AllMessage.factory(event));
    }

    @EventHandler
    public void onMessage(@NotNull MessagePostSendEvent event) throws Exception {
        save(AllMessage.factory(event));
    }

    @EventHandler
    public void onMessage(@NotNull GroupMessageSyncEvent event) throws Exception {
        save(AllMessage.factory(event));
    }

    @EventHandler
    public void onMessage(@NotNull FriendMessageEvent event) throws Exception {
        save(AllMessage.factory(event));
        for (SingleMessage singleMessage : event.getMessage()) {
            if (singleMessage instanceof FlashImage) {
                FlashImage image = (FlashImage) singleMessage;
                Friend friend = event.getFriend();
                if (isListening(event.getBot(), "f", friend.getId())) return;
                MessageChainBuilder builder = new MessageChainBuilder();
                builder.append("'").append(friend.getNick()).append("(" + friend.getId() + ")'在私聊").append("发送了[闪照]:")
                        .append(image.getImage());
                event.getBot().getAsFriend().sendMessage(builder.build());
            }
        }
    }

    private boolean isListening(Bot event, String t, long friend) {
        QueryWrapper<GroupConf> qw = new QueryWrapper<>();
        qw.eq("qid", event.getId());
        qw.eq("tid", t + friend);
        GroupConf groupConf = groupConfMapper.selectOne(qw);
        if (groupConf != null) {
            if (!groupConf.getK1()) return true;
        }
        return false;
    }

    @EventHandler
    public void onMessage(@NotNull FriendMessageSyncEvent event) throws Exception {
        save(AllMessage.factory(event));
    }

    @EventHandler
    public void onMessage(@NotNull StrangerMessageEvent event) throws Exception {
        save(AllMessage.factory(event));
    }

    @EventHandler
    public void onMessage(@NotNull StrangerMessageSyncEvent event) throws Exception {
        save(AllMessage.factory(event));
    }


    private Contact[] all(MessageRecallEvent recallEvent) {
        return new Contact[]{recallEvent.getBot().getAsFriend()};
    }

    @Autowired
    GroupConfMapper groupConfMapper;

    @EventHandler
    public void onMessage(MessageRecallEvent.GroupRecall event) {
        Group group = event.getGroup();
        Member member = event.getOperator();
        if (isListening(event.getBot(), "g", group.getId())) return;
        AllMessage all = getMessage(event);
        if (all != null) {
            Message m0 = MessageChain.deserializeFromJsonString(all.getContent());
            MessageChainBuilder builder = new MessageChainBuilder();
            builder.append("'").append(member.getNameCard()).append("(" + member.getId() + ")").append("'在群聊'")
                    .append(event.getGroup().getName()).append("(" + group.getId() + ")'撤回消息:").append(m0);
            Message message = builder.build();
            for (Contact contact : all(event)) {
                contact.sendMessage(message);
            }
        }
    }

    @EventHandler
    public void onMessage(MessageRecallEvent.FriendRecall event) {
        Friend friend = event.getAuthor();
        if (isListening(event.getBot(), "f", friend.getId())) return;
        AllMessage all = getMessage(event);
        if (all != null) {
            Message m0 = MessageChain.deserializeFromJsonString(all.getContent());
            MessageChainBuilder builder = new MessageChainBuilder();
            builder.append("'").append(friend.getNick()).append("(" + friend.getId() + ")'在私聊").append("撤回了:").append(m0);
            Message message = builder.build();
            for (Contact contact : all(event)) {
                contact.sendMessage(message);
            }
        }
    }

    public synchronized AllMessage getMessage(MessageRecallEvent event) {
        return getMessage(event.getMessageIds()[0], event.getMessageInternalIds()[0], String.valueOf(event.getBot().getId()));
    }

    public synchronized AllMessage getMessage(int id, int iid, String bid) {
        QueryWrapper<AllMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id);
        wrapper.eq("internal_id", iid);
        wrapper.eq("bot_id", bid);
        List<AllMessage> msg = saveMapper.selectList(wrapper);
        if (msg != null && msg.size() > 0) return msg.get(0);
        else return null;
    }
}
