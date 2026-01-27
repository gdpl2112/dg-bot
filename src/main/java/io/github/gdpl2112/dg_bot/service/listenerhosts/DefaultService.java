package io.github.gdpl2112.dg_bot.service.listenerhosts;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.dao.*;
import io.github.gdpl2112.dg_bot.mapper.*;
import io.github.gdpl2112.dg_bot.mapper.service.IStatisticsService;
import io.github.gdpl2112.dg_bot.service.BotService;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.common.Public;
import io.github.kloping.judge.Judge;
import io.github.kloping.map.MapUtils;
import io.github.kloping.url.UrlUtils;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.MessageChainBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author github-kloping
 * @since 2023-07-20
 */
@Slf4j
@Service
public class DefaultService extends net.mamoe.mirai.event.SimpleListenerHost implements CommandLineRunner {

    @Autowired
    BotService service;

    @Autowired
    Logger logger;

    @Autowired
    PassiveMapper passiveMapper;

    @Autowired
    ConfMapper confMapper;

    @Autowired
    GroupConfMapper groupConfMapper;

    @Autowired
    AdministratorMapper administratorMapper;

    @Autowired
    IStatisticsService statisticsService;

    @Autowired
    PassiveService passiveService;

    @Autowired
    private AuthMapper authMapper;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=================DefService=====================================");
    }

    private Conf getConf(Long bid) {
        Conf conf = confMapper.selectById(bid);
        if (conf == null) {
            conf = new Conf();
            conf.setQid(bid.toString());
            return conf;
        }
        return conf;
    }

    @EventHandler
    public void onEvent(GroupMessageEvent event) {
        String content = DgSerializer.messageChainSerializeToString(event.getMessage());
        if (Judge.isEmpty(content)) content = MessageChain.serializeToJsonString(event.getMessage());
        Long bid = event.getBot().getId();
        String tid = "g" + event.getSubject().getId();
        step(bid, event.getSender().getId(), tid, content.trim(), event.getSubject());
        statisticsService.statistics(Statistics.GROUP, bid.toString());
        ifispg(event.getSubject().getId(), event.getSender().getId(), content, event.getSubject());
    }

    @Value("${super.qid:3474006766}")
    Long superQid;

    private void ifispg(long id, long sid, String content, Group subject) {
        try {
            if (id == reportPointGid && sid == superQid) {
                if ("#1".equalsIgnoreCase(content)) {
                    subject.sendMessage(LocalDateTime.now().toString());
                }
            }
        } catch (Exception e) {
            log.error("Error in ifispg method", e);
        }
    }

    @Value("${report.point-gid}")
    private Long reportPointGid;

    @Autowired
    StatisticsMapper statisticsMapper;

    @EventHandler
    public void onEvent(GroupMessageSyncEvent event) {
        String content = DgSerializer.messageChainSerializeToString(event.getMessage());
        if (Judge.isEmpty(content)) content = MessageChain.serializeToJsonString(event.getMessage());
        Long bid = event.getBot().getId();
        Conf conf = getConf(bid);
        if (conf.getStatus0().equals(content)) {
            MessageChainBuilder builder = new MessageChainBuilder();
            builder.append(new At(event.getSender().getId()));
            try {
                builder.append(Contact.uploadImage(event.getSubject(), new URL(String.format("https://q1.qlogo.cn/g?b=qq&nk=%s&s=160", bid)).openStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            Integer grc = statisticsMapper.getTotalCount(bid.toString(), Statistics.GROUP);
            Integer frc = statisticsMapper.getTotalCount(bid.toString(), Statistics.PRIVATE);
            builder.append(Utils.getAllStatus(bid, authMapper)
                    + "\n---------------"
                    + "\n已处理群聊消息:" + grc + "条"
                    + "\n已处理私聊消息:" + frc + "条"
                    + "\n已处理总消息数:" + (grc + frc) + "条");
            event.getSubject().sendMessage(builder.build());
            return;
        }
        statisticsService.statistics(Statistics.GROUP, bid.toString());
        ifispg(event.getSubject().getId(), event.getSender().getId(), content, event.getSubject());
    }

    @EventHandler
    public void onEvent(FriendMessageEvent event) {
        String content = DgSerializer.messageChainSerializeToString(event.getMessage());
        if (Judge.isEmpty(content)) content = MessageChain.serializeToJsonString(event.getMessage());
        Long bid = event.getBot().getId();
        String tid = "f" + event.getSender().getId();
        step(bid, event.getSender().getId(), tid, content.trim(), event.getSubject());
        statisticsService.statistics(Statistics.PRIVATE, bid.toString());
    }

    @EventHandler
    public void onEvent(BotOnlineEvent event) {
        Public.EXECUTOR_SERVICE.submit(() -> {
            Conf conf = confMapper.selectById(event.getBot().getId());
            if (conf != null && Judge.isNotEmpty(conf.getNu())) {
                UrlUtils.getStringFromHttpUrl(conf.getNu() + URLEncoder.encode(event.toString()));
            }
        });
    }

    @EventHandler
    public void onEvent(BotOfflineEvent event) {
        Public.EXECUTOR_SERVICE.submit(() -> {
            Conf conf = confMapper.selectById(event.getBot().getId());
            if (conf != null && Judge.isNotEmpty(conf.getNu())) {
                UrlUtils.getStringFromHttpUrl(conf.getNu() + URLEncoder.encode(event.toString()));
            }
        });
    }

    private Map<Long, Map<Long, Passive>> adding = new HashMap<>();

    private void step(Long bid, Long sid, String tid, String content, Contact contact) {
        if (content.trim().isEmpty()) return;
        if (!isAdmin(bid, sid)) return;
        Conf conf = getConf(bid);
        Passive passive = null;
        if ((passive = Utils.getValueOrDefault(adding, bid, sid, null)) != null) {
            if (conf.getCancel0().equals(content)) {
                adding.get(bid).put(sid, null);
                contact.sendMessage("已取消!");
                return;
            } else if (passive.getTouch() == null) {
                passive.setTouch(filterTouch(content));
                contact.sendMessage("触发词设置完成");
            } else {
                passive.setOut(content);
                contact.sendMessage(passiveMapper.insert(passive) > 0 ? "保存成功!" : "保存失败!");
                adding.get(bid).put(sid, null);
            }
            return;
        }
        if (conf.getAdd0().equals(content)) {
            Passive p = new Passive();
            p.setQid(bid.toString());
            MapUtils.append(adding, bid, sid, p);
            contact.sendMessage("请依次发送'触发词','回复词'\n添加过程中可随时'" + conf.getCancel0());
            return;
        }
        if (content.startsWith(conf.getDel0())) {
            String touch = content.substring(conf.getDel0().length());
            QueryWrapper<Passive> qw = new QueryWrapper<>();
            qw.eq("qid", bid);
            qw.eq("touch", touch);
            int n = passiveMapper.delete(qw);
            contact.sendMessage(n > 0 ? "删除成功!" : "删除失败!");
            return;
        }
        if (content.startsWith(conf.getRetell())) {
            contact.sendMessage(DgSerializer.stringDeserializeToMessageChain(
                    content.substring(conf.getRetell().length()), contact.getBot(), contact));
            return;
        }
        //开回复
        if (content.startsWith(conf.getOpen0())) {
            QueryWrapper<GroupConf> qw = new QueryWrapper<>();
            qw.eq("qid", bid);
            qw.eq("tid", tid);
            GroupConf groupConf = groupConfMapper.selectOne(qw);
            if (groupConf != null) {
                groupConf.setK2(true);
                groupConfMapper.update(groupConf, qw);
            } else {
                groupConf = new GroupConf();
                groupConf.setQid(bid.toString());
                groupConf.setTid(tid);
                groupConfMapper.insert(groupConf);
            }
            contact.sendMessage("已开启!");
            return;
        }
        //开调用
        if (content.startsWith(conf.getOpen1())) {
            QueryWrapper<GroupConf> qw = new QueryWrapper<>();
            qw.eq("qid", bid);
            qw.eq("tid", tid);
            GroupConf groupConf = groupConfMapper.selectOne(qw);
            if (groupConf != null) {
                groupConf.setK0(true);
                groupConfMapper.update(groupConf, qw);
            } else {
                groupConf = new GroupConf();
                groupConf.setQid(bid.toString());
                groupConf.setTid(tid);
                groupConfMapper.insert(groupConf);
            }
            contact.sendMessage("已开启!");
            return;
        }
        //关回复
        if (content.startsWith(conf.getClose0())) {
            QueryWrapper<GroupConf> qw = new QueryWrapper<>();
            qw.eq("qid", bid);
            qw.eq("tid", tid);
            GroupConf groupConf = groupConfMapper.selectOne(qw);
            if (groupConf != null) {
                groupConf.setK2(false);
                groupConfMapper.update(groupConf, qw);
            } else {
                groupConf = new GroupConf();
                groupConf.setQid(bid.toString());
                groupConf.setTid(tid);
                groupConf.setK2(false);
                groupConfMapper.insert(groupConf);
            }
            contact.sendMessage("已关闭!");
            return;
        }
        //关调用
        if (content.startsWith(conf.getClose1())) {
            QueryWrapper<GroupConf> qw = new QueryWrapper<>();
            qw.eq("qid", bid);
            qw.eq("tid", tid);
            GroupConf groupConf = groupConfMapper.selectOne(qw);
            if (groupConf != null) {
                groupConf.setK0(false);
                groupConfMapper.update(groupConf, qw);
            } else {
                groupConf = new GroupConf();
                groupConf.setQid(bid.toString());
                groupConf.setTid(tid);
                groupConf.setK1(false);
                groupConfMapper.insert(groupConf);
            }
            contact.sendMessage("已关闭!");
            return;
        }
        if (content.startsWith(conf.getSelect0())) {
            content = content.substring(conf.getSelect0().length());
            List<Passive> passives = passiveService.getPassiveList(bid.toString(), content);
            if (passives != null && !passives.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                int i = 1;
                for (Passive pe : passives) {
                    sb.append(i++ + "." + pe.getOut()).append("\n");
                }
                contact.sendMessage(sb.toString().trim());
            } else contact.sendMessage("查询结果为空!");
            return;
        }
    }

    public String filterTouch(String content) {
        return content;
    }

    public boolean isAdmin(Long bid, Long sid) {
        LambdaQueryWrapper<Administrator> qwa = new LambdaQueryWrapper<>();
        qwa.eq(Administrator::getQid, bid).eq(Administrator::getTargetId, sid);
        Administrator administrator = administratorMapper.selectOne(qwa);
        return administrator != null && administrator.getQid().equals(bid.toString());
    }
}
