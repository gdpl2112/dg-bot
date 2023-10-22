package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.dao.Conf;
import io.github.gdpl2112.dg_bot.dao.Passive;
import io.github.gdpl2112.dg_bot.mapper.ConfMapper;
import io.github.gdpl2112.dg_bot.mapper.GroupConfMapper;
import io.github.gdpl2112.dg_bot.mapper.PassiveMapper;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.judge.Judge;
import io.github.kloping.map.MapUtils;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.MessageChain;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author github-kloping
 * @date 2023-07-20
 */
@Service
public class PassiveService extends net.mamoe.mirai.event.SimpleListenerHost  {

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

    @EventHandler
    public void onEvent(GroupMessageEvent event) {
        String content = DgSerializer.messageChainSerializeToString(event.getMessage());
        if (Judge.isEmpty(content)) content = MessageChain.serializeToJsonString(event.getMessage());
        step(event.getBot().getId(), "g" + event.getGroup().getId(), content, event.getSubject());
    }

    @EventHandler
    public void onEvent(FriendMessageEvent event) {
        String content = DgSerializer.messageChainSerializeToString(event.getMessage());
        if (Judge.isEmpty(content)) content = MessageChain.serializeToJsonString(event.getMessage());
        step(event.getBot().getId(), "f" + event.getFriend().getId(), content, event.getSubject());
    }

    public Map<Long, Map<String, Long>> cdMap = new HashMap<>();

    @Autowired
    ConfigService configService;

    public void step(Long bid, String tid, String content, Contact contact) {
        if (configService.isNotOpenK2(bid, tid)) return;
        synchronized (Utils.getBpSync(bid)) {
            long cd = Utils.getValueOrDefault(cdMap, bid, tid, 1L);
            if (System.currentTimeMillis() > cd) {
                String out = null;
                List<Passive> passives = getPassiveList(bid.toString(), content);
                if (passives != null && !passives.isEmpty()) {
                    out = Utils.getRandT(passives).getOut();
                    if (out != null) {
                        try {
                            MessageChain msg = null;
                            if (possible(out)) {
                                msg = MessageChain.deserializeFromJsonString(out);
                            }
                            if (msg == null || msg.isEmpty())
                                msg = DgSerializer.stringDeserializeToMessageChain(out, contact.getBot(), contact);
                            contact.sendMessage(msg);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Conf conf = confMapper.selectById(bid);
                        Long c0 = 1000L;
                        if (conf != null) {
                            c0 = conf.getCd0() * 1000L;
                        }
                        MapUtils.append(cdMap, bid, tid, System.currentTimeMillis() + c0);
                    }
                }
            }
        }
    }

    private boolean possible(String content) {
        return content.startsWith("[{") && content.endsWith("}]");
    }

    @Nullable
    public List<Passive> getPassiveList(String bid, String content) {
        QueryWrapper<Passive> qw1 = new QueryWrapper<Passive>();
        qw1.eq("qid", bid);
        qw1.eq("touch", content.trim());
        List<Passive> passives = passiveMapper.selectList(qw1);
        if (passives == null || passives.isEmpty()) {
            qw1.clear();
            qw1.eq("qid", bid);
            for (Passive pe : passiveMapper.selectList(qw1)) {
                if (content.matches(pe.getTouch())) {
                    passives.add(pe);
                }
            }
        }
        return passives;
    }
}
