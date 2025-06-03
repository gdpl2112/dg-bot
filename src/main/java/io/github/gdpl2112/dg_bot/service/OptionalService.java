package io.github.gdpl2112.dg_bot.service;

import io.github.gdpl2112.dg_bot.DgMain;
import io.github.gdpl2112.dg_bot.dao.Optional;
import io.github.gdpl2112.dg_bot.dto.OptionalDto;
import io.github.gdpl2112.dg_bot.mapper.OptionalMapper;
import io.github.gdpl2112.dg_bot.service.optionals.BaseOptional;
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
@Service
public class OptionalService implements ListenerHost {
    private void touchEvent(MessageEvent event, String id, String tid) {
        if (configService.isNotOpenK0(event.getBot().getId(), tid)) return;
        getBos().forEach((k, v) -> {
            if (isOpen(id, tid, k)) {
                v.run(event);
            }
        });
    }

    @Autowired
    OptionalMapper optionalMapper;

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

    private synchronized Map<String, BaseOptional> getBos() {
        if (!bos.isEmpty()) return bos;
        for (String beanName : DgMain.applicationContext.getBeanNamesForType(BaseOptional.class)) {
            BaseOptional bo = (BaseOptional) DgMain.applicationContext.getBean(beanName);
            bos.put(beanName, bo);
        }
        return bos;
    }

    private Map<String, BaseOptional> bos = new HashMap<>();

    public List<OptionalDto> getOptionalDtos(String id) {
        List<OptionalDto> dtos = new LinkedList<>();
        getBos().forEach((k, v) -> {
            Optional one = optionalMapper.selectByQidAndOpt(id, k);
            if (one == null) {
                one = new Optional();
                one.setQid(id);
                one.setOpt(k);
                one.setOpen(false);
            }
            dtos.add(OptionalDto.of(one, v.getName(), v.getDesc()));
        });
        return dtos;
    }

    @Autowired
    ConfigService configService;

    public boolean isOpen(String id, String tid, String name) {
        Optional optional = optionalMapper.selectByQidAndOpt(id, name);
        if (optional == null ? false : optional.getOpen()) {
            return !configService.isNotOpenK2(Long.valueOf(id), tid);
        } else return false;
    }
}
