package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.DgMain;
import io.github.gdpl2112.dg_bot.dao.Optional;
import io.github.gdpl2112.dg_bot.dto.OptionalDto;
import io.github.gdpl2112.dg_bot.mapper.OptionalMapper;
import io.github.gdpl2112.dg_bot.service.optionals.BaseOptional;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.BotOnlineEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

/**
 * @author github.kloping
 */
@Service
public class OptionalService implements ListenerHost {
    @Autowired
    OptionalMapper optionalMapper;

    @EventHandler
    public void online(BotOnlineEvent event) {
        String id = String.valueOf(event.getBot().getId());
        for (OptionalDto optionalDto : getOptionalDtos(id)) {
            if (!optionalDto.getOpen()) {
                BaseOptional bo = (BaseOptional) DgMain.applicationContext.getBean(optionalDto.getOpt());
                event.getBot().getEventChannel().registerListenerHost(bo);
            }
        }
    }

    public List<OptionalDto> getOptionalDtos(String id) {
        QueryWrapper<Optional> qw = new QueryWrapper<>();
        qw.eq("qid", id);
        List<OptionalDto> dtos = new LinkedList<>();
        for (String beanName : DgMain.applicationContext.getBeanNamesForType(BaseOptional.class)) {
            BaseOptional bo = (BaseOptional) DgMain.applicationContext.getBean(beanName);
            qw.eq("opt", beanName);
            Optional one = optionalMapper.selectOne(qw);
            if (one == null) {
                one = new Optional();
                one.setQid(id);
                one.setOpt(beanName);
                one.setOpen(false);
            }
            dtos.add(OptionalDto.of(one,bo.getName(),bo.getDesc()));
        }
        return dtos;
    }
}
