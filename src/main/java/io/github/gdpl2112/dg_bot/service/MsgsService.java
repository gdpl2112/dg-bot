package io.github.gdpl2112.dg_bot.service;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.github.gdpl2112.dg_bot.dao.Msgs;
import kotlin.coroutines.CoroutineContext;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.OnlineMessageSource;
import net.mamoe.mirai.message.data.PlainText;
import net.mamoe.mirai.message.data.SingleMessage;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * @author github.kloping
 */
@Service
public class MsgsService extends SimpleListenerHost {

    @Override
    public void handleException(@NotNull CoroutineContext context, @NotNull Throwable exception) {
        exception.printStackTrace();
    }

    @EventHandler
    public void onMessage(@NotNull GroupMessageEvent event) throws Exception {
        save(event);
    }

    @EventHandler
    public void onMessage(@NotNull MessagePostSendEvent event) throws Exception {
        Msgs msgs = new Msgs();
        msgs.setTime(System.currentTimeMillis());

        OnlineMessageSource.Outgoing messageSource = event.getReceipt().getSource();
        Contact subj = messageSource.getSubject();
        if (subj instanceof Group) {
            msgs.setName(((Group) subj).getName());
            msgs.setType("group");
        } else if (subj instanceof Friend) {
            msgs.setName(((Friend) subj).getNick());
            msgs.setType("friend");
        }
        msgs.setSubjectId(String.valueOf(messageSource.getSubject().getId()));
        msgs.setSenderId(String.valueOf(messageSource.getSender().getId()));
        msgs.setBotId(String.valueOf(messageSource.getBot().getId()));
        msgs.setSenderName(messageSource.getSender().getNick());

        StringBuilder sb = null;
        for (SingleMessage singleMessage : event.getMessage()) {
            if (singleMessage instanceof PlainText) {
                PlainText text = (PlainText) singleMessage;
                if (sb == null) sb = new StringBuilder();
                sb.append(text.getContent());
            } else if (singleMessage instanceof Image) {
                String url = Image.queryUrl((Image) singleMessage);
                msgs.setImageUrl(url);
            }
        }
        msgs.setMsg(sb == null ? "" : sb.toString());
        insert0(msgs);
    }

    @EventHandler
    public void onMessage(@NotNull GroupMessageSyncEvent event) throws Exception {
        save(event);
    }

    @EventHandler
    public void onMessage(@NotNull FriendMessageSyncEvent event) throws Exception {
        save(event);
    }

    @EventHandler
    public void onMessage(@NotNull StrangerMessageEvent event) throws Exception {
        save(event);
    }

    @EventHandler
    public void onMessage(@NotNull StrangerMessageSyncEvent event) throws Exception {
        save(event);
    }

    private void save(@NotNull MessageEvent event) {
        Msgs msgs = new Msgs();
        Contact subj = event.getSubject();
        if (subj instanceof Group) {
            msgs.setName(((Group) subj).getName());
            msgs.setType("group");
        } else if (subj instanceof Friend) {
            msgs.setName(((Friend) subj).getNick());
            msgs.setType("friend");
        }
        msgs.setTime(System.currentTimeMillis());
        msgs.setSubjectId(String.valueOf(event.getSubject().getId()));
        msgs.setSenderId(String.valueOf(event.getSender().getId()));
        msgs.setBotId(String.valueOf(event.getBot().getId()));
        msgs.setSenderName(event.getSender().getNick());
        StringBuilder sb = null;
        for (SingleMessage singleMessage : event.getMessage()) {
            if (singleMessage instanceof PlainText) {
                PlainText text = (PlainText) singleMessage;
                if (sb == null) sb = new StringBuilder();
                sb.append(text.getContent());
            } else if (singleMessage instanceof Image) {
                String url = Image.queryUrl((Image) singleMessage);
                msgs.setImageUrl(url);
            }
        }
        msgs.setMsg(sb == null ? "" : sb.toString());
        insert0(msgs);
    }

    @Value("${spring.datasource.msgs.url}")
    private String url;
    @Value("${spring.datasource.msgs.driver-class-name}")
    private String dcname;
    @Value("${spring.datasource.msgs.username}")
    private String username;
    @Value("${spring.datasource.msgs.password}")
    private String password;

    private JdbcTemplate template = null;

    public JdbcTemplate jdbcTemplate() {
        if (template != null) return template;
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        template = new JdbcTemplate();
        template.setDataSource(dataSource);
        return template;
    }

    private void insert0(Msgs msgs) {
        try {
            //sql, 每行加空格
            String sql = "INSERT INTO `msgs` (`bot_id`, `name`, `subject_id`, `sender_name`, `sender_id`, `msg`, `image_url`, `time`, `type`) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";

            PreparedStatement ptmt = jdbcTemplate().getDataSource().getConnection().prepareStatement(sql); //预编译SQL，减少sql执行

            ptmt.setString(1, msgs.getBotId());
            ptmt.setString(2, msgs.getName());
            ptmt.setString(3, msgs.getSubjectId());
            ptmt.setString(4, msgs.getSenderName());
            ptmt.setString(5, msgs.getSenderId());
            ptmt.setString(6, msgs.getMsg());
            ptmt.setString(7, msgs.getImageUrl());
            ptmt.setLong(8, msgs.getTime());
            ptmt.setString(9, msgs.getType());

            ptmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static final RowMapper<Msgs> rowMapper = BeanPropertyRowMapper.newInstance(Msgs.class);

    public List<Msgs> msgsList(UserDetails userDetails, Long time) {
        String sql = null;
        if (time == null) {
            sql = String.format("SELECT * FROM msgs WHERE bot_id='%s' ORDER BY `time` DESC LIMIT %s,%s;", userDetails.getUsername(), 0, 100);
        } else {
            sql = String.format("SELECT * FROM msgs WHERE bot_id='%s' AND `time`<%s ORDER BY `time` DESC LIMIT %s,%s;", userDetails.getUsername(), 0, 100);
        }
        return jdbcTemplate().query(sql, rowMapper);
    }


}
