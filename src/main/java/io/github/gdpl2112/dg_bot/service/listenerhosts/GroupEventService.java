package io.github.gdpl2112.dg_bot.service.listenerhosts;

import cn.evolvefield.onebot.client.connection.WSGolab;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.GroupConf;
import io.github.gdpl2112.dg_bot.mapper.GroupConfMapper;
import io.github.gdpl2112.dg_bot.service.ManageDbService;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.MemberJoinEvent;
import net.mamoe.mirai.event.events.MemberLeaveEvent;
import net.mamoe.mirai.event.events.MemberMuteEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 群事件监听服务，负责监听指定配置群中的踢人和禁言事件，
 * 并将事件数据写入对应 QQ 账号下的 {bid}-manage.db SQLite 文件。
 * 只处理在 group_conf 表中有配置记录的群。
 */
@Slf4j
@Service
public class GroupEventService extends SimpleListenerHost {

    @Autowired
    private GroupConfMapper groupConfMapper;

    @Autowired
    private ManageDbService manageDbService;

    public GroupEventService() {
        super();
//        WSGolab.INSTANCE.getMsgReceiveList().add(msg -> {
//            System.out.println("recv11:-->" + msg);
//            return false;
//        });
    }

    /**
     * 判断指定群是否开启了事件记录（group_conf.k4 = true）
     *
     * @param bid     bot QQ 账号
     * @param groupId 群号
     * @return true 表示已开启事件记录，false 表示未配置或未开启
     */
    private boolean isConfiguredGroup(long bid, long groupId) {
        QueryWrapper<GroupConf> qw = new QueryWrapper<>();
        qw.eq("qid", bid);
        qw.eq("tid", "g" + groupId);
        GroupConf groupConf = groupConfMapper.selectOne(qw);
        // 群配置存在且事件记录开关(k4)已开启
        return groupConf != null && Boolean.TRUE.equals(groupConf.getK4());
    }

    /**
     * 监听群成员被踢出事件，记录：被踢的人、操作者、群号、时间
     *
     * @param event MemberLeaveEvent.Kick 踢人事件
     */
    @EventHandler
    public void onKick(@NotNull MemberLeaveEvent.Kick event) {
        long bid = event.getBot().getId();
        long groupId = event.getGroup().getId();

        // 仅处理已配置群
        if (!isConfiguredGroup(bid, groupId)) return;

        long targetId = event.getMember().getId();
        // 操作者可能为 null（被机器人踢出时），此时用 bot 自身 ID 代替
        long operatorId = event.getOperator() != null
                ? event.getOperator().getId()
                : bid;
        long time = System.currentTimeMillis();

        manageDbService.insertKick(bid, groupId, targetId, operatorId, time);
        log.info("记录踢人事件 bot={} group={} target={} operator={}", bid, groupId, targetId, operatorId);
    }

    /**
     * 监听群成员被禁言事件，记录：被禁言的人、操作者、群号、时间
     *
     * @param event MemberMuteEvent 禁言事件
     */
    @EventHandler
    public void onMute(@NotNull MemberMuteEvent event) {
        long bid = event.getBot().getId();
        long groupId = event.getGroup().getId();

        // 仅处理已配置群
        if (!isConfiguredGroup(bid, groupId)) return;

        long targetId = event.getMember().getId();
        // 操作者可能为 null（被机器人禁言时），此时用 bot 自身 ID 代替
        long operatorId = event.getOperator() != null
                ? event.getOperator().getId()
                : bid;
        int duration = event.getDurationSeconds();
        long time = System.currentTimeMillis();

        manageDbService.insertMute(bid, groupId, targetId, operatorId, duration, time);
        log.info("记录禁言事件 bot={} group={} target={} operator={} duration={}s",
                bid, groupId, targetId, operatorId, duration);
    }

    //MemberJoinEvent.Active
    @EventHandler
    public void onJoin(@NotNull MemberJoinEvent event) {
        long bid = event.getBot().getId();
        long groupId = event.getGroup().getId();

        // 仅处理已配置群
        if (!isConfiguredGroup(bid, groupId)) return;

        long targetId = event.getMember().getId();
        long time = System.currentTimeMillis();

        // manageDbService.insertJoin(bid, groupId, targetId, time);
        log.info("记录入群事件 bot={} group={} target={}", bid, groupId, targetId);
    }
}
