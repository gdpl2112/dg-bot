package io.github.gdpl2112.dg_bot.service.listenerhosts;

import io.github.gdpl2112.dg_bot.DgMain;
import io.github.gdpl2112.dg_bot.dao.Optional;
import io.github.gdpl2112.dg_bot.dto.OptionalDto;
import io.github.gdpl2112.dg_bot.mapper.OptionalMapper;
import io.github.gdpl2112.dg_bot.service.ConfigService;
import io.github.gdpl2112.dg_bot.service.optionals.BaseOptional;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author github.kloping
 */
@Slf4j
@Service
public class OptionalService implements ListenerHost {
    private void touchEvent(MessageEvent event, String bid, String tid) {
        if (configService.isNotOpenK3(event.getBot().getId(), tid)) return;
        Map<String, BaseOptional> bos = getBos();
        if (bos == null || bos.isEmpty()) {
            log.warn("no optional,waiting server started!");
        } else bos.forEach((k, v) -> {
            if (isOpen(bid, k)) {
                v.run(event);
            }
        });
    }

    @Autowired
    public OptionalMapper optionalMapper;

    @EventHandler
    public void onEvent(GroupMessageEvent event) {
        String id = String.valueOf(event.getBot().getId());
        String tid = "g" + event.getSubject().getId();
        touchEvent(event, id, tid);
    }

    @EventHandler
    public void onEvent(GroupMessageSyncEvent event) {
        String id = String.valueOf(event.getBot().getId());
        String tid = "g" + event.getSubject().getId();
        touchEvent(event, id, tid);
    }

    @EventHandler
    public void onEvent(FriendMessageEvent event) {
        String id = String.valueOf(event.getBot().getId());
        String tid = "f" + event.getSubject().getId();
        touchEvent(event, id, tid);
    }

    @EventHandler
    public void onEvent(FriendMessageSyncEvent event) {
        String id = String.valueOf(event.getBot().getId());
        String tid = "f" + event.getSubject().getId();
        touchEvent(event, id, tid);
    }

    public synchronized Map<String, BaseOptional> getBos() {
        if (!bos.isEmpty()) return bos;
        if (DgMain.applicationContext == null) return null;
        for (String beanName : DgMain.applicationContext.getBeanNamesForType(BaseOptional.class)) {
            BaseOptional bo = (BaseOptional) DgMain.applicationContext.getBean(beanName);
            bos.put(beanName, bo);
        }
        return bos;
    }

    private Map<String, BaseOptional> bos = new HashMap<>();

    public List<OptionalDto> getOptionalDtos(String id) {
        List<OptionalDto> dtos = new LinkedList<>();
        Map<String, BaseOptional> bos = getBos();
        if (bos == null || bos.isEmpty()) return dtos;
        bos.forEach((k, v) -> {
            Optional one = optionalMapper.selectByQidAndOpt(id, k);
            if (one == null) {
                one = new Optional();
                one.setQid(id);
                one.setOpt(k);
                one.setOpen(false);
                optionalMapper.insert(one);
            }
            dtos.add(OptionalDto.of(one, v.getName(), v.getDesc()));
        });
        return dtos;
    }

    @Autowired
    ConfigService configService;

    public boolean isOpen(String id, String name) {
        Optional optional = optionalMapper.selectByQidAndOpt(id, name);
        if (optional == null ? false : optional.getOpen()) {
            return true;
        } else return false;
    }
}
