package io.github.gdpl2112.dg_bot.service.listenerhosts;

import io.github.gdpl2112.dg_bot.DgMain;
import io.github.gdpl2112.dg_bot.dao.Optional;
import io.github.gdpl2112.dg_bot.dto.OptionalDto;
import io.github.gdpl2112.dg_bot.mapper.OptionalMapper;
import io.github.gdpl2112.dg_bot.service.optionals.BaseOptional;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author github.kloping
 */
@Slf4j
@Service
public class OptionalService implements ListenerHost {
    @Autowired
    public OptionalMapper optionalMapper;
    private Map<String, BaseOptional> bos = new HashMap<>();

    private void touchEvent(MessageEvent event, String bid, String tid) {
        Map<String, BaseOptional> bos = getBos();
        if (bos == null || bos.isEmpty()) {
            log.warn("no optional,waiting server started!");
        } else bos.forEach((k, v) -> {
            if (isOpen(bid, k, tid)) {
                v.run(event);
            }
        });
    }

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

    /**
     * 获取某用户所有可选功能的配置列表
     * 每个功能附带已启用的群/好友 tid 列表
     */
    public List<OptionalDto> getOptionalDtos(String id) {
        List<OptionalDto> dtos = new LinkedList<>();
        Map<String, BaseOptional> bos = getBos();
        if (bos == null || bos.isEmpty()) return dtos;
        // 查询该用户所有 optional 记录
        List<Optional> allOpts = optionalMapper.selectByQid(id);
        // 按 opt 分组
        Map<String, List<Optional>> optMap = allOpts.stream()
                .collect(Collectors.groupingBy(Optional::getOpt));

        bos.forEach((k, v) -> {
            List<Optional> optList = optMap.getOrDefault(k, Collections.emptyList());
            // 收集已启用的 tid 列表
            List<String> enabledTids = optList.stream()
                    .filter(o -> Boolean.TRUE.equals(o.getOpen()))
                    .map(Optional::getTid)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            // 构造一个代表记录（用第一条或新建默认）
            Optional one;
            if (!optList.isEmpty()) {
                one = optList.get(0);
            } else {
                one = new Optional();
                one.setQid(id);
                one.setOpt(k);
                one.setTid("*");
                one.setOpen(false);
            }
            dtos.add(OptionalDto.of(one, v.getName(), v.getDesc(), enabledTids));
        });
        return dtos;
    }

    /**
     * 判断某功能在某群/好友是否启用
     * 优先查 tid 精确匹配，再查 tid="*" 的全局配置
     * 无记录默认不启用（需要用户主动设置哪些群启用）
     */
    public boolean isOpen(String id, String name, String tid) {
        // 先查精确匹配
        Optional optional = optionalMapper.selectByQidAndOptAndTid(id, name, tid);
        if (optional != null) return Boolean.TRUE.equals(optional.getOpen());
        // 再查全局匹配 tid="*"
        Optional global = optionalMapper.selectByQidAndOptAndTid(id, name, "*");
        if (global != null) return Boolean.TRUE.equals(global.getOpen());
        // 无记录默认不启用
        return false;
    }

    /**
     * 为某功能设置某个群的启用状态
     */
    public boolean setGroupEnabled(String qid, String opt, String tid, boolean enabled) {
        Optional optional = optionalMapper.selectByQidAndOptAndTid(qid, opt, tid);
        if (optional != null) {
            optional.setOpen(enabled);
            optionalMapper.update(optional, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Optional>()
                    .eq("qid", qid).eq("opt", opt).eq("tid", tid));
        } else {
            optional = new Optional();
            optional.setQid(qid);
            optional.setOpt(opt);
            optional.setTid(tid);
            optional.setOpen(enabled);
            optionalMapper.insert(optional);
        }
        return true;
    }

    /**
     * 获取某功能已启用的群/好友 tid 列表
     */
    public List<String> getEnabledGroups(String qid, String opt) {
        List<Optional> list = optionalMapper.selectByQidAndOpt(qid, opt);
        return list.stream()
                .filter(o -> Boolean.TRUE.equals(o.getOpen()))
                .map(Optional::getTid)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
