package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.service.ManageDbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 群管理事件记录查询接口，提供踢人和禁言记录的分页查询及操作者排行功能。
 * 所有接口均通过登录认证，数据来源为当前 bot 账号对应的 {bid}-manage.db。
 *
 * <pre>
 * 接口列表：
 *   GET /api/manage/kick                  分页查询踢人记录
 *   GET /api/manage/kick/top-operators    踢人操作者排行
 *   GET /api/manage/mute                  分页查询禁言记录
 *   GET /api/manage/mute/top-operators    禁言操作者排行
 *   GET /api/manage/approve               分页查询批准入群记录
 *   GET /api/manage/approve/top-operators 批准入群操作者排行
 * </pre>
 */
@RestController
@RequestMapping("/api/manage")
@PreAuthorize("hasAnyAuthority('user','manage')")
public class ManageController {

    @Autowired
    private ManageDbService manageDbService;

    // ─── 踢人记录 ─────────────────────────────────────────────────────────────

    /**
     * 分页查询踢人记录
     *
     * @param userDetails 当前登录用户（bid 即 QQ 账号）
     * @param gid         群号过滤，不传或传 0 表示全部群
     * @param startTime   开始时间戳（ms），不传或传 0 表示不限制
     * @param endTime     结束时间戳（ms），不传或传 0 表示不限制
     * @param page        页码，从 1 开始，默认 1
     * @param size        每页大小，默认 20，最大 100
     * @return JSON：{ total, page, size, list:[{id,group_id,target_id,operator_id,time},...] }
     */
    @GetMapping("/kick")
    public Object queryKick(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        long bid = Long.parseLong(userDetails.getUsername());
        // 每页上限 100 条，防止过量查询
        size = Math.min(size, 100);
        page = Math.max(page, 1);

        long total = manageDbService.countKick(bid, gid, startTime, endTime);
        List<Map<String, Object>> list = manageDbService.queryKick(bid, gid, startTime, endTime, page, size);

        JSONObject result = new JSONObject();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("list", list);
        return result;
    }

    /**
     * 查询指定时间范围内踢人次数最多的操作者排行
     *
     * @param userDetails 当前登录用户
     * @param gid         群号过滤，0 表示全部群
     * @param startTime   开始时间戳（ms），0 表示不限制
     * @param endTime     结束时间戳（ms），0 表示不限制
     * @param limit       返回条数，默认 10，最大 50
     * @return JSON：[ {operator_id, cnt}, ... ]（按操作次数降序）
     */
    @GetMapping("/kick/top-operators")
    public Object topKickOperators(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "10") int limit) {

        long bid = Long.parseLong(userDetails.getUsername());
        limit = Math.min(limit, 50);
        return manageDbService.topKickOperators(bid, gid, startTime, endTime, limit);
    }

    // ─── 禁言记录 ─────────────────────────────────────────────────────────────

    /**
     * 分页查询禁言记录
     *
     * @param userDetails 当前登录用户（bid 即 QQ 账号）
     * @param gid         群号过滤，不传或传 0 表示全部群
     * @param startTime   开始时间戳（ms），不传或传 0 表示不限制
     * @param endTime     结束时间戳（ms），不传或传 0 表示不限制
     * @param page        页码，从 1 开始，默认 1
     * @param size        每页大小，默认 20，最大 100
     * @return JSON：{ total, page, size, list:[{id,group_id,target_id,operator_id,duration,time},...] }
     */
    @GetMapping("/mute")
    public Object queryMute(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        long bid = Long.parseLong(userDetails.getUsername());
        size = Math.min(size, 100);
        page = Math.max(page, 1);

        long total = manageDbService.countMute(bid, gid, startTime, endTime);
        List<Map<String, Object>> list = manageDbService.queryMute(bid, gid, startTime, endTime, page, size);

        JSONObject result = new JSONObject();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("list", list);
        return result;
    }

    /**
     * 查询指定时间范围内禁言次数最多的操作者排行
     *
     * @param userDetails 当前登录用户
     * @param gid         群号过滤，0 表示全部群
     * @param startTime   开始时间戳（ms），0 表示不限制
     * @param endTime     结束时间戳（ms），0 表示不限制
     * @param limit       返回条数，默认 10，最大 50
     * @return JSON：[ {operator_id, cnt}, ... ]（按操作次数降序）
     */
    @GetMapping("/mute/top-operators")
    public Object topMuteOperators(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "10") int limit) {

        long bid = Long.parseLong(userDetails.getUsername());
        limit = Math.min(limit, 50);
        return manageDbService.topMuteOperators(bid, gid, startTime, endTime, limit);
    }

    // ─── 批准入群记录 ──────────────────────────────────────────────────────────

    /**
     * 分页查询批准入群记录
     *
     * @param userDetails 当前登录用户（bid 即 QQ 账号）
     * @param gid         群号过滤，不传或传 0 表示全部群
     * @param startTime   开始时间戳（ms），不传或传 0 表示不限制
     * @param endTime     结束时间戳（ms），不传或传 0 表示不限制
     * @param page        页码，从 1 开始，默认 1
     * @param size        每页大小，默认 20，最大 100
     * @return JSON：{ total, page, size, list:[{id,group_id,req_id,operator_id,time},...] }
     */
    @GetMapping("/approve")
    public Object queryApprove(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        long bid = Long.parseLong(userDetails.getUsername());
        // 每页上限 100 条，防止过量查询
        size = Math.min(size, 100);
        page = Math.max(page, 1);

        long total = manageDbService.countApprove(bid, gid, startTime, endTime);
        List<Map<String, Object>> list = manageDbService.queryApprove(bid, gid, startTime, endTime, page, size);

        JSONObject result = new JSONObject();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("list", list);
        return result;
    }

    /**
     * 查询指定时间范围内批准入群次数最多的操作者排行
     *
     * @param userDetails 当前登录用户
     * @param gid         群号过滤，0 表示全部群
     * @param startTime   开始时间戳（ms），0 表示不限制
     * @param endTime     结束时间戳（ms），0 表示不限制
     * @param limit       返回条数，默认 10，最大 50
     * @return JSON：[ {operator_id, cnt}, ... ]（按操作次数降序）
     */
    @GetMapping("/approve/top-operators")
    public Object topApproveOperators(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "10") int limit) {

        long bid = Long.parseLong(userDetails.getUsername());
        limit = Math.min(limit, 50);
        return manageDbService.topApproveOperators(bid, gid, startTime, endTime, limit);
    }
}
