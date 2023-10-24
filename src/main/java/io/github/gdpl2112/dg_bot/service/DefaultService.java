package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.dao.Administrator;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.dao.GroupConf;
import io.github.gdpl2112.dg_bot.dao.Passive;
import io.github.gdpl2112.dg_bot.mapper.AdministratorMapper;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.GroupConfMapper;
import io.github.gdpl2112.dg_bot.mapper.PassiveMapper;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.common.Public;
import io.github.kloping.judge.Judge;
import io.github.kloping.map.MapUtils;
import io.github.kloping.url.UrlUtils;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.events.BotOfflineEvent;
import net.mamoe.mirai.event.events.BotOnlineEvent;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.MessageChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author github-kloping
 * @date 2023-07-20
 */
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
    PassiveService passiveService;

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
    }

    @EventHandler
    public void onEvent(FriendMessageEvent event) {
        String content = DgSerializer.messageChainSerializeToString(event.getMessage());
        if (Judge.isEmpty(content)) content = MessageChain.serializeToJsonString(event.getMessage());
        Long bid = event.getBot().getId();
        String tid = "f" + event.getSender().getId();
        step(bid, event.getSender().getId(), tid, content.trim(), event.getSubject());
    }

    @Autowired
    RestTemplate template;

    @EventHandler
    public void onEvent(BotOnlineEvent event) {
        Public.EXECUTOR_SERVICE.submit(() -> {
            Conf conf = confMapper.selectById(event.getBot().getId());
            if (conf != null && Judge.isNotEmpty(conf.getNu())) {
                UrlUtils.getStringFromHttpUrl(conf.getNu() + event.toString());
            }
        });
    }

    @EventHandler
    public void onEvent(BotOfflineEvent event) {
        Public.EXECUTOR_SERVICE.submit(() -> {
            Conf conf = confMapper.selectById(event.getBot().getId());
            if (conf != null && Judge.isNotEmpty(conf.getNu())) {
                UrlUtils.getStringFromHttpUrl(conf.getNu() + event.toString());
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
            }
            if (passive.getTouch() == null) {
                passive.setTouch(filterTouch(content));
                contact.sendMessage("设置完成");
            } else {
                passive.setOut(content);
                contact.sendMessage(passiveMapper.insert(passive) > 0 ? "成功!" : "失败!");
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
        if (content.startsWith(conf.getRetell())) {
            contact.sendMessage(DgSerializer.stringDeserializeToMessageChain(
                    content.substring(conf.getRetell().length()), contact.getBot(), contact));
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
        //开监听
        if (content.startsWith(conf.getOpen1())) {
            QueryWrapper<GroupConf> qw = new QueryWrapper<>();
            qw.eq("qid", bid);
            qw.eq("tid", tid);
            GroupConf groupConf = groupConfMapper.selectOne(qw);
            if (groupConf != null) {
                groupConf.setK1(true);
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
        //关监听
        if (content.startsWith(conf.getClose1())) {
            QueryWrapper<GroupConf> qw = new QueryWrapper<>();
            qw.eq("qid", bid);
            qw.eq("tid", tid);
            GroupConf groupConf = groupConfMapper.selectOne(qw);
            if (groupConf != null) {
                groupConf.setK1(false);
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

    private boolean isAdmin(Long bid, Long sid) {
        QueryWrapper<Administrator> qwa = new QueryWrapper<>();
        qwa.eq("qid", bid);
        for (Administrator administrator : administratorMapper.selectList(qwa)) {
            if (administrator.getTargetId().equals(sid.toString())) {
                return true;
            }
        }
        return false;
    }
}
