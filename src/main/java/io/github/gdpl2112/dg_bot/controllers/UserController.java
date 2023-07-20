package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.Administrator;
import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.dao.GroupConf;
import io.github.gdpl2112.dg_bot.mapper.AdministratorMapper;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.gdpl2112.dg_bot.mapper.GroupConfMapper;
import io.github.kloping.judge.Judge;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author github.kloping
 */
@RestController
@PreAuthorize("hasAuthority('user')")
public class UserController {
    @Autowired
    AuthMapper authMapper;

    @RequestMapping("/user")
    public Object user(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            JSONObject jo = new JSONObject();
            AuthM authM = authMapper.selectById(userDetails.getUsername());
            Long qid = Long.valueOf(authM.getQid());
            Bot bot = Bot.getInstanceOrNull(qid);
            if (bot != null && bot.isOnline()) {
                String nick = bot.getNick();
                if (Judge.isEmpty(nick)) nick = qid.toString();
                jo.put("nickname", nick);
                jo.put("t0", authM.getT0());
            } else{
                jo.put("nickname", "未在线");
                jo.put("t0", -1L);
            }
            jo.put("qid", authM.getQid());
            jo.put("icon", String.format("https://q1.qlogo.cn/g?b=qq&nk=%s&s=640", qid));
            jo.put("expire", authM.getExp());
            return jo.toString();
        }
        return null;
    }

    @RequestMapping("/statistics")
    public Object statistics(@AuthenticationPrincipal UserDetails userDetails) {
        return null;
    }

    //==============================================================
    @Autowired
    AdministratorMapper administratorMapper;

    @RequestMapping("mlist")
    public List<Administrator> mlist(@AuthenticationPrincipal UserDetails userDetails) {
        QueryWrapper<Administrator> qw = new QueryWrapper<>();
        qw.eq("qid", userDetails.getUsername());
        return administratorMapper.selectList(qw);
    }

    @RequestMapping("mdel")
    public Boolean mdel(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String id) {
        QueryWrapper<Administrator> qw = new QueryWrapper<>();
        qw.eq("qid", userDetails.getUsername());
        qw.eq("target_id", id);
        return administratorMapper.delete(qw) > 0;
    }

    @RequestMapping("m_add")
    public Boolean m_add(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String id) {
        QueryWrapper<Administrator> qw = new QueryWrapper<>();
        qw.eq("qid", userDetails.getUsername());
        qw.eq("target_id", id);
        if (administratorMapper.selectList(qw).size() <= 0) {
            Administrator administrator = new Administrator();
            administrator.setQid(userDetails.getUsername());
            administrator.setTargetId(id);
            return administratorMapper.insert(administrator) > 0;
        } else return false;
    }

    //==============================================================

    @Autowired
    GroupConfMapper groupConfMapper;

    @RequestMapping("glist")
    public List glist(@AuthenticationPrincipal UserDetails userDetails) {
        QueryWrapper<GroupConf> qw = new QueryWrapper<>();
        qw.eq("qid", userDetails.getUsername());
        List<GroupConf> confs = groupConfMapper.selectList(qw);
        Map<String, GroupConf> tid2conf = new HashMap<>();
        for (GroupConf conf : confs) tid2conf.put(conf.getTid(), conf);
        Bot bot = Bot.getInstanceOrNull(Long.valueOf(userDetails.getUsername()));
        List outList = new ArrayList();
        if (bot != null) {
            for (Friend friend : bot.getFriends()) {
                String tid = "f" + friend.getId();
                JSONObject jo = new JSONObject();
                if (tid2conf.containsKey(tid)) {
                    GroupConf f0 = tid2conf.get(tid);
                    jo.put("k1", f0.getK1());
                    jo.put("k2", f0.getK2());
                } else {
                    jo.put("k1", true);
                    jo.put("k2", true);
                }
                jo.put("tid", tid);
                jo.put("name", friend.getRemark());
                jo.put("icon", friend.getAvatarUrl());
                outList.add(jo);
            }

            for (Group group : bot.getGroups()) {
                String tid = "g" + group.getId();
                JSONObject jo = new JSONObject();
                if (tid2conf.containsKey(tid)) {
                    GroupConf f0 = tid2conf.get(tid);
                    jo.put("k1", f0.getK1());
                    jo.put("k2", f0.getK2());
                } else {
                    jo.put("k1", true);
                    jo.put("k2", true);
                }
                jo.put("tid", tid);
                jo.put("name", group.getName());
                jo.put("icon", group.getAvatarUrl());
                outList.add(jo);
            }
        } else {
            for (GroupConf conf : confs) {
                JSONObject jo = new JSONObject();
                jo.put("k1", conf.getK1());
                jo.put("k2", conf.getK2());
                jo.put("tid", conf.getTid());
                jo.put("name", conf.getTid());
                jo.put("icon",
                        conf.getTid().substring(0, 1).equals("g") ?
                                String.format("http://p.qlogo.cn/gh/%s/%s/spec", conf.getTid().substring(1)) :
                                String.format("https://q1.qlogo.cn/g?b=qq&nk=%s&s=640", conf.getTid().substring(1)));
            }
        }
        return outList;
    }

    @RequestMapping("gc1")
    public Boolean change1(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String tid) {
        try {
            QueryWrapper<GroupConf> qw = new QueryWrapper<>();
            qw.eq("qid", userDetails.getUsername());
            qw.eq("tid", tid);
            GroupConf groupConf = groupConfMapper.selectOne(qw);
            if (groupConf != null) {
                groupConf.setK1(!groupConf.getK1());
                groupConfMapper.update(groupConf, qw);
            } else {
                groupConf = new GroupConf();
                groupConf.setQid(userDetails.getUsername());
                groupConf.setTid(tid);
                groupConf.setK1(false);
                groupConf.setK2(true);
                groupConfMapper.insert(groupConf);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @RequestMapping("gc2")
    public Boolean change2(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String tid) {
        try {
            QueryWrapper<GroupConf> qw = new QueryWrapper<>();
            qw.eq("qid", userDetails.getUsername());
            qw.eq("tid", tid);
            GroupConf groupConf = groupConfMapper.selectOne(qw);
            if (groupConf != null) {
                groupConf.setK2(!groupConf.getK2());
                groupConfMapper.update(groupConf, qw);
            } else {
                groupConf = new GroupConf();
                groupConf.setQid(userDetails.getUsername());
                groupConf.setTid(tid);
                groupConf.setK1(true);
                groupConf.setK2(false);
                groupConfMapper.insert(groupConf);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
