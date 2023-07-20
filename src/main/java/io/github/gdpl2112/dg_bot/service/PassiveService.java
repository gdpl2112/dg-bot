package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.dao.GroupConf;
import io.github.gdpl2112.dg_bot.dao.Passive;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.GroupConfMapper;
import io.github.gdpl2112.dg_bot.mapper.PassiveMapper;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.map.MapUtils;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.code.MiraiCode;
import net.mamoe.mirai.message.data.Message;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author github-kloping
 * @date 2023-07-20
 */
@Service
public class PassiveService extends net.mamoe.mirai.event.SimpleListenerHost implements CommandLineRunner {
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

    @Override
    public void run(String... args) throws Exception {
    }

    @EventHandler
    public void onEvent(GroupMessageEvent event) {
        String content = MiraiCode.serializeToMiraiCode((Iterable<? extends Message>) event.getMessage());
        step(event.getBot().getId(), "g" + event.getGroup().getId(), content, event.getSubject());
    }

    @EventHandler
    public void onEvent(FriendMessageEvent event) {
        String content = MiraiCode.serializeToMiraiCode((Iterable<? extends Message>) event.getMessage());
        step(event.getBot().getId(), "f" + event.getFriend().getId(), content, event.getSubject());
    }

    public Map<Long, Map<String, Long>> cdMap = new HashMap<>();

    public void step(Long bid, String tid, String content, Contact contact) {
        QueryWrapper<GroupConf> qw = new QueryWrapper<>();
        qw.eq("qid", bid);
        qw.eq("tid", tid);
        GroupConf groupConf = groupConfMapper.selectOne(qw);
        if (groupConf != null) {
            if (!groupConf.getK2()) return;
        }
        long cd = Utils.getValueOrDefault(cdMap, bid, tid, 0L);
        if (System.currentTimeMillis() > cd) {
            String out = null;
            List<Passive> passives = getPassiveList(content);
            if (passives != null && !passives.isEmpty()) {
                out = Utils.getRandT(passives).getOut();
                if (out != null) {
                    try {
                        contact.sendMessage(MiraiCode.deserializeMiraiCode(out));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Conf conf = confMapper.selectById(bid);
                    if (conf != null) {
                        MapUtils.append(cdMap, bid, tid, System.currentTimeMillis() + conf.getCd0() * 1000L);
                    } else {
                        MapUtils.append(cdMap, bid, tid, System.currentTimeMillis() + 1000L);
                    }
                }
            }
        }
    }

    @Nullable
    public List<Passive> getPassiveList(String content) {
        QueryWrapper qw1 = new QueryWrapper();
        qw1.eq("touch", content.trim());
        List<Passive> passives = passiveMapper.selectList(qw1);
        if (passives == null || passives.isEmpty()) {
            for (Passive pe : passiveMapper.selectList(null)) {
                if (content.matches(pe.getTouch())) {
                    passives.add(pe);
                }
            }
        }
        return passives;
    }
}
