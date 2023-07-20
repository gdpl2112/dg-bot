package io.github.gdpl2112.dg_bot.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.action.MemberNudge;
import net.mamoe.mirai.message.code.MiraiCode;
import net.mamoe.mirai.message.data.*;

/**
 * @author github-kloping
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class AllMessage {
    private Long time;
    private Integer id;
    private Integer internalId;
    private Long senderId;
    private Long botId;
    private String type;
    private Long fromId;
    private String content;
    private Integer recalled = 0;

    public static AllMessage factory(MessagePostSendEvent event) {
        OnlineMessageSource.Outgoing messageSource = event.getReceipt().getSource();
        if (messageSource instanceof OnlineMessageSource.Outgoing.ToGroup) {
            MessageChain mc = event.getMessage();
            return new AllMessage()
                    .setBotId(event.getBot().getId())
                    .setId(latest(0, messageSource.getIds()))
                    .setContent(getText(mc))
                    .setFromId(messageSource.getTargetId())
                    .setSenderId(event.getBot().getId())
                    .setInternalId(latest(0, messageSource.getInternalIds()))
                    .setType("groupSelf").setTime(System.currentTimeMillis());
        } else if (messageSource instanceof OnlineMessageSource.Outgoing.ToFriend) {
            MessageChain mc = event.getMessage();
            return new AllMessage()
                    .setBotId(event.getBot().getId())
                    .setId(latest(0, messageSource.getIds()))
                    .setContent(getText(mc))
                    .setFromId(messageSource.getTargetId())
                    .setSenderId(event.getBot().getId())
                    .setInternalId(latest(0, messageSource.getInternalIds()))
                    .setType("friendSelf").setTime(System.currentTimeMillis());
        }
        return null;
    }

    public static AllMessage factory(MessageEvent event) {
        MessageSource messageSource = (MessageSource) event.getMessage().get(0);
        if (event instanceof GroupMessageEvent) {
            GroupMessageEvent gme = (GroupMessageEvent) event;
            return new AllMessage()
                    .setBotId(event.getBot().getId())
                    .setId(latest(0, messageSource.getIds()))
                    .setContent(getText(gme.getMessage()))
                    .setFromId(gme.getSubject().getId())
                    .setSenderId(gme.getSender().getId())
                    .setInternalId(latest(0, messageSource.getInternalIds()))
                    .setType("group").setTime(System.currentTimeMillis());
        } else if (event instanceof GroupMessageSyncEvent) {
            GroupMessageSyncEvent gme = (GroupMessageSyncEvent) event;
            return new AllMessage()
                    .setBotId(event.getBot().getId())
                    .setId(latest(0, messageSource.getIds()))
                    .setContent(getText(gme.getMessage()))
                    .setFromId(gme.getSubject().getId())
                    .setSenderId(gme.getSender().getId())
                    .setInternalId(latest(0, messageSource.getInternalIds()))
                    .setType("groupSelfSync").setTime(System.currentTimeMillis());
        } else if (event instanceof FriendMessageEvent) {
            FriendMessageEvent gme = (FriendMessageEvent) event;
            return new AllMessage()
                    .setBotId(event.getBot().getId())
                    .setId(latest(0, messageSource.getIds()))
                    .setContent(getText(gme.getMessage()))
                    .setFromId(gme.getSubject().getId())
                    .setSenderId(gme.getSender().getId())
                    .setInternalId(latest(0, messageSource.getInternalIds()))
                    .setType("friend").setTime(System.currentTimeMillis());
        } else if (event instanceof FriendMessageSyncEvent) {
            FriendMessageSyncEvent gme = (FriendMessageSyncEvent) event;
            return new AllMessage()
                    .setBotId(event.getBot().getId())
                    .setId(latest(0, messageSource.getIds()))
                    .setContent(getText(gme.getMessage()))
                    .setFromId(gme.getSubject().getId())
                    .setSenderId(gme.getSender().getId())
                    .setInternalId(latest(0, messageSource.getInternalIds()))
                    .setType("friendSelfSync").setTime(System.currentTimeMillis());
        } else if (event instanceof StrangerMessageEvent) {
            FriendMessageEvent gme = (FriendMessageEvent) event;
            return new AllMessage()
                    .setBotId(event.getBot().getId())
                    .setId(latest(0, messageSource.getIds()))
                    .setContent(getText(gme.getMessage()))
                    .setFromId(gme.getSubject().getId())
                    .setSenderId(gme.getSender().getId())
                    .setInternalId(latest(0, messageSource.getInternalIds()))
                    .setType("stranger").setTime(System.currentTimeMillis());
        } else if (event instanceof StrangerMessageSyncEvent) {
            FriendMessageSyncEvent gme = (FriendMessageSyncEvent) event;
            return new AllMessage()
                    .setBotId(event.getBot().getId())
                    .setId(latest(0, messageSource.getIds()))
                    .setContent(getText(gme.getMessage()))
                    .setFromId(gme.getSubject().getId())
                    .setSenderId(gme.getSender().getId())
                    .setInternalId(latest(0, messageSource.getInternalIds()))
                    .setType("strangerSelf").setTime(System.currentTimeMillis());
        }
        return new AllMessage();
    }

    public static final int latest(int defaultValue, int... ts) {
        if (ts.length == 0 || ts[ts.length - 1] == 0) {
            return defaultValue;
        } else {
            return ts[ts.length - 1];
        }
    }

    public static final <T> T latest(T defaultValue, T... ts) {
        if (ts.length == 0 || ts[ts.length - 1] == null) {
            return defaultValue;
        } else {
            return ts[ts.length - 1];
        }
    }

    private static String getText(MessageChain chain) {
        return MiraiCode.serializeToMiraiCode((Iterable<? extends Message>) chain);
    }

    public int getIntTime() {
        Long t0 = time;
        return Integer.parseInt(t0.toString().substring(0, 10));
    }
}
