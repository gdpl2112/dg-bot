package io.github.gdpl2112.dg_bot.controllers;

import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.service.ManageDbService;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.MemberPermission;
import net.mamoe.mirai.contact.NormalMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 群管理事件记录查询接口，提供踢人和禁言记录的分页查询及操作者排行功能。
 * 页面公开预览（凭 manage.key 访问），数据来源固定为配置 manage.bid（默认 super.qid）
 * 对应的 {bid}-manage.db，不依赖登录用户身份。
 *
 * <pre>
 * 接口列表：
 *   GET /api/manage/kick                  分页查询踢人记录（支持 gid/operator 过滤）
 *   GET /api/manage/kick/top-operators    踢人操作者排行
 *   GET /api/manage/mute                  分页查询禁言记录
 *   GET /api/manage/mute/top-operators    禁言操作者排行
 *   GET /api/manage/approve               分页查询批准入群记录
 *   GET /api/manage/approve/top-operators 批准入群操作者排行
 *   GET /api/manage/groups                所有已记录数据的群号列表（供前端群聊过滤）
 *   GET /api/manage/operators             已记录操作者 ID 与实际群管理/群主 ID 的合并列表
 * </pre>
 */
@RestController
@RequestMapping("/api/manage")
@PreAuthorize("hasAnyAuthority('user','manage')")
public class ManageController {

    @Autowired
    private ManageDbService manageDbService;

    /** 数据来源 bot 账号，取配置 manage.bid（默认 super.qid）。页面公开预览，不随登录态变化。 */
    @Value("${manage.bid}")
    private long manageBid;

    // ─── 踢人记录 ─────────────────────────────────────────────────────────────

    /**
     * 分页查询踢人记录
     *
     * @param gid         群号过滤，不传或传 0 表示全部群
     * @param operator    操作者 ID 过滤，不传或传 0 表示全部操作者
     * @param startTime   开始时间戳（ms），不传或传 0 表示不限制
     * @param endTime     结束时间戳（ms），不传或传 0 表示不限制
     * @param page        页码，从 1 开始，默认 1
     * @param size        每页大小，默认 20，最大 100
     * @return JSON：{ total, page, size, list:[{id,group_id,target_id,operator_id,time},...] }
     */
    @GetMapping("/kick")
    public Object queryKick(
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long operator,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        long bid = manageBid;
        // 每页上限 100 条，防止过量查询
        size = Math.min(size, 100);
        page = Math.max(page, 1);

        long total = manageDbService.countKick(bid, gid, operator, startTime, endTime);
        List<Map<String, Object>> list = manageDbService.queryKick(bid, gid, operator, startTime, endTime, page, size);

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
     * @param gid         群号过滤，0 表示全部群
     * @param startTime   开始时间戳（ms），0 表示不限制
     * @param endTime     结束时间戳（ms），0 表示不限制
     * @param limit       返回条数，默认 20，最大 50
     * @return JSON：[ {operator_id, cnt}, ... ]（按操作次数降序）
     */
    @GetMapping("/kick/top-operators")
    public Object topKickOperators(
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "20") int limit) {

        long bid = manageBid;
        limit = Math.min(limit, 50);
        return manageDbService.topKickOperators(bid, gid, startTime, endTime, limit);
    }

    // ─── 禁言记录 ─────────────────────────────────────────────────────────────

    /**
     * 分页查询禁言记录
     *
     * @param gid         群号过滤，不传或传 0 表示全部群
     * @param operator    操作者 ID 过滤，不传或传 0 表示全部操作者
     * @param startTime   开始时间戳（ms），不传或传 0 表示不限制
     * @param endTime     结束时间戳（ms），不传或传 0 表示不限制
     * @param page        页码，从 1 开始，默认 1
     * @param size        每页大小，默认 20，最大 100
     * @return JSON：{ total, page, size, list:[{id,group_id,target_id,operator_id,duration,time},...] }
     */
    @GetMapping("/mute")
    public Object queryMute(
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long operator,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        long bid = manageBid;
        size = Math.min(size, 100);
        page = Math.max(page, 1);

        long total = manageDbService.countMute(bid, gid, operator, startTime, endTime);
        List<Map<String, Object>> list = manageDbService.queryMute(bid, gid, operator, startTime, endTime, page, size);

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
     * @param gid         群号过滤，0 表示全部群
     * @param startTime   开始时间戳（ms），0 表示不限制
     * @param endTime     结束时间戳（ms），0 表示不限制
     * @param limit       返回条数，默认 20，最大 50
     * @return JSON：[ {operator_id, cnt}, ... ]（按操作次数降序）
     */
    @GetMapping("/mute/top-operators")
    public Object topMuteOperators(
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "20") int limit) {

        long bid = manageBid;
        limit = Math.min(limit, 50);
        return manageDbService.topMuteOperators(bid, gid, startTime, endTime, limit);
    }

    // ─── 批准入群记录 ──────────────────────────────────────────────────────────

    /**
     * 分页查询批准入群记录
     *
     * @param gid         群号过滤，不传或传 0 表示全部群
     * @param operator    操作者 ID 过滤，不传或传 0 表示全部操作者
     * @param startTime   开始时间戳（ms），不传或传 0 表示不限制
     * @param endTime     结束时间戳（ms），不传或传 0 表示不限制
     * @param page        页码，从 1 开始，默认 1
     * @param size        每页大小，默认 20，最大 100
     * @return JSON：{ total, page, size, list:[{id,group_id,req_id,operator_id,time},...] }
     */
    @GetMapping("/approve")
    public Object queryApprove(
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long operator,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        long bid = manageBid;
        // 每页上限 100 条，防止过量查询
        size = Math.min(size, 100);
        page = Math.max(page, 1);

        long total = manageDbService.countApprove(bid, gid, operator, startTime, endTime);
        List<Map<String, Object>> list = manageDbService.queryApprove(bid, gid, operator, startTime, endTime, page, size);

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
     * @param gid         群号过滤，0 表示全部群
     * @param startTime   开始时间戳（ms），0 表示不限制
     * @param endTime     结束时间戳（ms），0 表示不限制
     * @param limit       返回条数，默认 20，最大 50
     * @return JSON：[ {operator_id, cnt}, ... ]（按操作次数降序）
     */
    @GetMapping("/approve/top-operators")
    public Object topApproveOperators(
            @RequestParam(defaultValue = "0") long gid,
            @RequestParam(defaultValue = "0") long startTime,
            @RequestParam(defaultValue = "0") long endTime,
            @RequestParam(defaultValue = "20") int limit) {

        long bid = manageBid;
        limit = Math.min(limit, 50);
        return manageDbService.topApproveOperators(bid, gid, startTime, endTime, limit);
    }

    // ─── 过滤辅助接口 ──────────────────────────────────────────────────────────

    /**
     * 获取所有已记录过数据（踢人 / 禁言 / 批准入群）的群号列表。
     * <p>修复点：前端群聊过滤下拉框原先只能依赖 bot 当前在线群列表，bot 已退群但仍有历史记录的群
     * 无法被选中过滤。此接口直接从记录表汇总，保证所有出现过的群都能被选择。</p>
     *
     * @return JSON 数组：["群号", ...]（按群号升序）
     */
    @GetMapping("/groups")
    public Object listGroups() {
        long bid = manageBid;
        return manageDbService.listGroupIds(bid);
    }

    /**
     * 获取「已记录操作者 ID」与「实际群聊中的管理员 / 群主 ID」合并去重后的列表，供前端选择过滤查询。
     * <p>来源合并：
     * <ul>
     *   <li>recorded：在踢人 / 禁言 / 批准入群记录中出现过的 operator_id；</li>
     *   <li>实际群管理：当前 bot 在线群里权限为 OWNER / ADMINISTRATOR 的成员。</li>
     * </ul>
     * 当 gid &gt; 0 时仅扫描该群的管理员，否则扫描全部群。</p>
     *
     * @param gid         群号过滤，0 表示全部群
     * @return JSON 数组：[ {id, recorded(bool), role("owner"/"admin"/"")}, ... ]（按 id 升序）
     */
    @GetMapping("/operators")
    public Object listOperators(
            @RequestParam(defaultValue = "0") long gid) {

        long bid = manageBid;

        // 以 id 为 key 聚合，保持插入顺序，最终按数值排序
        Map<String, JSONObject> merged = new LinkedHashMap<>();

        // 1) 记录表中出现过的操作者
        for (String opId : manageDbService.listOperatorIds(bid)) {
            JSONObject jo = new JSONObject();
            jo.put("id", opId);
            jo.put("recorded", true);
            jo.put("role", "");
            merged.put(opId, jo);
        }

        // 2) 实际群聊中的管理员 / 群主
        Bot bot = Bot.getInstanceOrNull(bid);
        if (bot != null && bot.isOnline()) {
            for (Group group : bot.getGroups()) {
                if (gid > 0 && group.getId() != gid) continue;
                for (NormalMember member : group.getMembers()) {
                    MemberPermission perm = member.getPermission();
                    if (perm != MemberPermission.OWNER && perm != MemberPermission.ADMINISTRATOR) continue;
                    String id = String.valueOf(member.getId());
                    JSONObject jo = merged.computeIfAbsent(id, k -> {
                        JSONObject n = new JSONObject();
                        n.put("id", id);
                        n.put("recorded", false);
                        n.put("role", "");
                        return n;
                    });
                    // OWNER 优先级高于 ADMINISTRATOR，不被覆盖
                    String role = perm == MemberPermission.OWNER ? "owner" : "admin";
                    if (!"owner".equals(jo.getString("role"))) jo.put("role", role);
                }
            }
        }

        List<JSONObject> list = new ArrayList<>(merged.values());
        list.sort((a, b) -> {
            try {
                return Long.compare(Long.parseLong(a.getString("id")), Long.parseLong(b.getString("id")));
            } catch (NumberFormatException e) {
                return a.getString("id").compareTo(b.getString("id"));
            }
        });
        return list;
    }
}
