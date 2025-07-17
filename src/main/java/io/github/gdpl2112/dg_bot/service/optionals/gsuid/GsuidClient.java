package io.github.gdpl2112.dg_bot.service.optionals.gsuid;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.service.optionals.BaseOptional;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.common.Public;
import io.github.kloping.judge.Judge;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.data.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author github.kloping
 */
@Component
public class GsuidClient extends WebSocketClient implements BaseOptional {

    @Autowired
    Logger logger;

    private String urla;

    public GsuidClient(@Value("${gsuid.url:def}") String url) throws URISyntaxException {
        super(new URI( url));
        urla = url;
        Public.EXECUTOR_SERVICE.submit(this);
    }

    @Override
    public String getDesc() {
        return "接入后台的gsuid from wuyi with genshinuid and wzryuid 插件";
    }

    @Override
    public String getName() {
        return "接入WUYI";
    }

    @Override
    public void run(MessageEvent event) {
        if (event instanceof GroupMessageEvent) {
            offer(event);
            sendToGsuid(event);
        } else if (event instanceof GroupMessageSyncEvent) {
            offer(event);
            sendToGsuid(event);
        } else if (event instanceof FriendMessageEvent) {
            offer(event);
            sendToGsuid(event);
        } else if (event instanceof FriendMessageSyncEvent) {
            offer(event);
            sendToGsuid(event);
        }
    }

    public void send(MessageReceive receive) {
        if (!this.isOpen()) return;
        send(JSON.toJSONString(receive).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void send(byte[] data) {
        super.send(data);
        String json = new String(data, Charset.forName("utf-8"));
        logger.info("send=>" + json);
    }

    @Override
    public void onMessage(String msg) {
        MessageOut out = JSONObject.parseObject(msg, MessageOut.class);
        String bsid = out.getBot_self_id();
        logger.info(String.format("gsuid msg bot(%s) to size: %s", bsid, msg.length()));
        MessageEvent raw = getMessage(out.getMsg_id());
        MessageChainBuilder builder = new MessageChainBuilder();
        if (raw != null) builder.append(new QuoteReply(raw.getSource()));
        for (MessageData d0 : out.getContent()) {
            if (d0.getType().equals("node")) {
                try {
                    JSONArray array = (JSONArray) d0.getData();
                    for (MessageData d1 : array.toJavaList(MessageData.class))
                        builderAppend(builder, d1, raw);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else builderAppend(builder, d0, raw);
        }
        Contact contact;
        if (raw != null) contact = raw.getSubject();
        else
            contact = out.getTarget_type().equals("direct") ? raw.getBot().getFriend(Long.parseLong(out.getTarget_id())) : raw.getBot().getGroup(Long.parseLong(out.getTarget_id()));
        contact.sendMessage(builder.build());

    }

    private void builderAppend(MessageChainBuilder builder, MessageData d0, MessageEvent event) {
        if (d0.getType().equals("text")) {
            builder.append(new PlainText(d0.getData().toString().trim())).append("\n");
        } else if (d0.getType().equals("image")) {
            byte[] bytes;
            if (d0.getData().toString().startsWith("base64://")) {
                bytes = Base64.getDecoder().decode(d0.getData().toString().substring("base64://".length()));
            } else {
                bytes = Base64.getDecoder().decode(d0.getData().toString());
            }
            Image image = Contact.uploadImage(event.getBot().getAsFriend(), new ByteArrayInputStream(bytes));
            builder.append(image);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.info("=============gsuid_core opened===========");
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        super.onMessage(bytes);
        String json = new String(bytes.array(), Charset.forName("utf-8"));
        onMessage(json);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.error(String.format("gsuid_coore close %s => %s", code, reason));
        if ("def".equalsIgnoreCase(urla)) return;
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                TimeUnit.SECONDS.sleep(10);
                reconnect();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    //connect

    private void sendToGsuid(MessageEvent event) {
        List<MessageData> list = getMessageData(event);
        if (!list.isEmpty()) {
            MessageReceive receive = new MessageReceive();
            receive.setBot_id("qqgroup0");
            receive.setBot_self_id(String.valueOf(event.getBot().getId()));
            receive.setUser_id(String.valueOf(event.getSender().getId()));
            receive.setMsg_id(getMessageEventId(event));
            receive.setUser_type("direct");
            receive.setGroup_id("");
            if (event instanceof GroupMessageEvent) {
                receive.setUser_type("group");
                receive.setGroup_id(String.valueOf(event.getSubject().getId()));
            }
            if (event.getSender().getId() == 3474006766L) receive.setUser_pm(0);
            else receive.setUser_pm(2);
            receive.setContent(list.toArray(new MessageData[0]));
            this.send(receive);
        }
    }

    @NotNull
    private static List<MessageData> getMessageData(MessageEvent event) {
        MessageChain chain = event.getMessage();
        List<MessageData> list = new ArrayList<>();
        chain.forEach(e -> {
            MessageData message = new MessageData();
            if (e instanceof PlainText) {
                message.setType("text");
                String data = e.toString().trim();
                if (data.equalsIgnoreCase("/gs帮助")) data = "gs帮助";
                message.setData(data);
            } else if (e instanceof Image) {
                Image image = (Image) e;
                message.setType("image");
                message.setData(Image.queryUrl(image));
            } else if (e instanceof At) {
                At at = (At) e;
                if (at.getTarget() == event.getBot().getId()) return;
                else {
                    message.setType("at");
                    message.setData(at.getTarget());
                }
            } else return;
            list.add(message);
        });
        return list;
    }

    //=============消息记录start
    public static final Integer MAX_E = 50;
    private MessageEvent temp0 = null;
    private Deque<MessageEvent> QUEUE = new LinkedList<>();


    public void offer(MessageEvent msg) {
        if (QUEUE.contains(msg)) return;
        if (QUEUE.size() >= MAX_E) QUEUE.pollLast();
        QUEUE.offerFirst(msg);
    }

    public MessageEvent getMessage(String id) {
        if (Judge.isEmpty(id)) return null;
        if (temp0 != null && getMessageEventId(temp0).equals(id)) return temp0;
        for (MessageEvent event : QUEUE) {
            if (getMessageEventId(event).equals(id)) return temp0 = event;
        }
        return null;
    }

    public String getMessageEventId(MessageEvent event) {
        if (event.getSource().getIds().length == 0) return "";
        else return String.valueOf(event.getSource().getIds()[0]);
    }
    //=============消息记录end
}
