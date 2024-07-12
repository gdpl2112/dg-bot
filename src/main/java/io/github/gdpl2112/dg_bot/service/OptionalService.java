package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.DgMain;
import io.github.gdpl2112.dg_bot.dao.Optional;
import io.github.gdpl2112.dg_bot.dto.OptionalDto;
import io.github.gdpl2112.dg_bot.mapper.OptionalMapper;
import io.github.gdpl2112.dg_bot.service.optionals.BaseOptional;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
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
    @Autowired
    OptionalMapper optionalMapper;

    @EventHandler
    public void onEvent(GroupMessageEvent event) {
        String id = String.valueOf(event.getBot().getId());
        getBos().forEach((k, v) -> {
            if (isOpen(id, k)) {
                v.run(event);
            }
        });
    }

    private synchronized Map<String, BaseOptional> getBos() {
        if (!bos.isEmpty()) return bos;
        for (String beanName : DgMain.applicationContext.getBeanNamesForType(BaseOptional.class)) {
            BaseOptional bo = (BaseOptional) DgMain.applicationContext.getBean(beanName);
            bos.put(beanName, bo);
        }
        return bos;
    }

    @EventHandler
    public void onEvent(FriendMessageEvent event) {
        String id = String.valueOf(event.getBot().getId());
        getBos().forEach((k, v) -> {
            if (isOpen(id, k)) {
                v.run(event);
            }
        });
    }

    private Map<String, BaseOptional> bos = new HashMap<>();

    public List<OptionalDto> getOptionalDtos(String id) {
        QueryWrapper<Optional> qw = new QueryWrapper<>();
        qw.eq("qid", id);
        List<OptionalDto> dtos = new LinkedList<>();
        getBos().forEach((k, v) -> {
            qw.eq("opt", k);
            Optional one = optionalMapper.selectOne(qw);
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

    public boolean isOpen(String id, String name) {
        QueryWrapper<Optional> qw = new QueryWrapper<>();
        qw.eq("qid", id);
        qw.eq("opt", name);
        Optional optional = optionalMapper.selectOne(qw);
        return optional == null ? false : optional.getOpen();
    }
}
