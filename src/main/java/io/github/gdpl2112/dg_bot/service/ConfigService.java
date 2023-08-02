package io.github.gdpl2112.dg_bot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.GroupConf;
import io.github.gdpl2112.dg_bot.mapper.GroupConfMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author github.kloping
 */
@Service
public class ConfigService {
    @Autowired
    GroupConfMapper groupConfMapper;

    public boolean isNotOpenK2(Long bid, String tid) {
        QueryWrapper<GroupConf> qw = new QueryWrapper<>();
        qw.eq("qid", bid);
        qw.eq("tid", tid);
        GroupConf groupConf = groupConfMapper.selectOne(qw);
        if (groupConf != null) if (!groupConf.getK2()) return true;
        return false;
    }

    public boolean isNotOpenK1(Long bid, String tid) {
        QueryWrapper<GroupConf> qw = new QueryWrapper<>();
        qw.eq("qid", bid);
        qw.eq("tid", tid);
        GroupConf groupConf = groupConfMapper.selectOne(qw);
        if (groupConf != null) if (!groupConf.getK1()) return true;
        return false;
    }

    public boolean isNotOpenK0(Long bid, String tid) {
        QueryWrapper<GroupConf> qw = new QueryWrapper<>();
        qw.eq("qid", bid);
        qw.eq("tid", tid);
        GroupConf groupConf = groupConfMapper.selectOne(qw);
        if (groupConf != null) if (!groupConf.getK0()) return true;
        return false;
    }
}
