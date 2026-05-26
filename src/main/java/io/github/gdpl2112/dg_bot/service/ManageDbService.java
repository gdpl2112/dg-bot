package io.github.gdpl2112.dg_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理数据库服务，负责维护每个 QQ 账号下的 {bid}-manage.db SQLite 文件。
 * 提供踢人记录和禁言记录的表初始化与数据写入功能。
 */
@Slf4j
@Service
public class ManageDbService {

    /** 每个 bot 账号对应一个独立的 JdbcTemplate，懒初始化 */
    private static final Map<Long, JdbcTemplate> TEMPLATE_MAP = new ConcurrentHashMap<>();

    /**
     * 获取指定 bot 账号的 JdbcTemplate，若不存在则创建并初始化表结构
     *
     * @param bid bot QQ 账号
     * @return 对应的 JdbcTemplate
     */
    public JdbcTemplate getTemplate(long bid) {
        return TEMPLATE_MAP.computeIfAbsent(bid, id -> {
            DataSource ds = new AbstractDataSource() {
                @Override
                public Connection getConnection() throws SQLException {
                    return DriverManager.getConnection(
                            String.format("jdbc:sqlite:%d-manage.db", id));
                }

                @Override
                public Connection getConnection(String username, String password) throws SQLException {
                    return DriverManager.getConnection(
                            String.format("jdbc:sqlite:%d-manage.db", id));
                }
            };
            JdbcTemplate tpl = new JdbcTemplate(ds);
            // 初始化踢人记录表
            tpl.execute("CREATE TABLE IF NOT EXISTS kick_record ("
                    + "id          INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "group_id    TEXT    NOT NULL, "
                    + "target_id   TEXT    NOT NULL, "
                    + "operator_id TEXT    NOT NULL, "
                    + "time        INTEGER NOT NULL"
                    + ");");
            // 初始化禁言记录表
            tpl.execute("CREATE TABLE IF NOT EXISTS mute_record ("
                    + "id          INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "group_id    TEXT    NOT NULL, "
                    + "target_id   TEXT    NOT NULL, "
                    + "operator_id TEXT    NOT NULL, "
                    + "duration    INTEGER NOT NULL, "
                    + "time        INTEGER NOT NULL"
                    + ");");
            log.info("ManageDb 初始化完成: {}-manage.db", id);
            return tpl;
        });
    }

    /**
     * 写入踢人事件记录
     *
     * @param bid        bot QQ 账号
     * @param groupId    群号
     * @param targetId   被踢的人
     * @param operatorId 操作者
     * @param time       事件发生时间戳（毫秒）
     */
    public void insertKick(long bid, long groupId, long targetId, long operatorId, long time) {
        try {
            getTemplate(bid).update(
                    "INSERT INTO kick_record (group_id, target_id, operator_id, time) VALUES (?, ?, ?, ?)",
                    String.valueOf(groupId),
                    String.valueOf(targetId),
                    String.valueOf(operatorId),
                    time);
        } catch (Exception e) {
            log.error("写入踢人记录失败 bid={} group={} target={}", bid, groupId, targetId, e);
        }
    }

    /**
     * 写入禁言事件记录
     *
     * @param bid             bot QQ 账号
     * @param groupId         群号
     * @param targetId        被禁言的人
     * @param operatorId      操作者
     * @param durationSeconds 禁言时长（秒），0 表示解除禁言
     * @param time            事件发生时间戳（毫秒）
     */
    public void insertMute(long bid, long groupId, long targetId, long operatorId, int durationSeconds, long time) {
        try {
            getTemplate(bid).update(
                    "INSERT INTO mute_record (group_id, target_id, operator_id, duration, time) VALUES (?, ?, ?, ?, ?)",
                    String.valueOf(groupId),
                    String.valueOf(targetId),
                    String.valueOf(operatorId),
                    durationSeconds,
                    time);
        } catch (Exception e) {
            log.error("写入禁言记录失败 bid={} group={} target={}", bid, groupId, targetId, e);
        }
    }

    // ─── 查询方法 ─────────────────────────────────────────────────────────────

    /**
     * 构建 WHERE 子句及参数（供 kick_record / mute_record 通用）
     *
     * @param groupId   群号，0 表示不过滤
     * @param startTime 开始时间戳（ms），0 表示不过滤
     * @param endTime   结束时间戳（ms），0 表示不过滤
     * @param params    出参：绑定参数列表
     * @return WHERE 子句字符串（含前导空格，若无条件则返回空串）
     */
    private String buildWhere(long groupId, long startTime, long endTime, List<Object> params) {
        StringBuilder where = new StringBuilder();
        if (groupId > 0) {
            where.append(where.length() == 0 ? " WHERE " : " AND ").append("group_id = ?");
            params.add(String.valueOf(groupId));
        }
        if (startTime > 0) {
            where.append(where.length() == 0 ? " WHERE " : " AND ").append("time >= ?");
            params.add(startTime);
        }
        if (endTime > 0) {
            where.append(where.length() == 0 ? " WHERE " : " AND ").append("time <= ?");
            params.add(endTime);
        }
        return where.toString();
    }

    /**
     * 分页查询踢人记录
     *
     * @param bid       bot QQ 账号
     * @param groupId   群号，0 表示不过滤
     * @param startTime 开始时间戳（ms），0 表示不过滤
     * @param endTime   结束时间戳（ms），0 表示不过滤
     * @param page      页码，从 1 开始
     * @param size      每页大小
     * @return 记录列表，每条为 Map（id/group_id/target_id/operator_id/time）
     */
    public List<Map<String, Object>> queryKick(long bid, long groupId, long startTime, long endTime, int page, int size) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(groupId, startTime, endTime, params);
        params.add(size);
        params.add((page - 1) * size);
        String sql = "SELECT id, group_id, target_id, operator_id, time FROM kick_record"
                + where + " ORDER BY time DESC LIMIT ? OFFSET ?";
        return getTemplate(bid).queryForList(sql, params.toArray());
    }

    /**
     * 统计踢人记录总数
     *
     * @param bid       bot QQ 账号
     * @param groupId   群号，0 表示不过滤
     * @param startTime 开始时间戳（ms），0 表示不过滤
     * @param endTime   结束时间戳（ms），0 表示不过滤
     * @return 符合条件的记录总数
     */
    public long countKick(long bid, long groupId, long startTime, long endTime) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(groupId, startTime, endTime, params);
        String sql = "SELECT COUNT(*) FROM kick_record" + where;
        Long cnt = getTemplate(bid).queryForObject(sql, Long.class, params.toArray());
        return cnt == null ? 0L : cnt;
    }

    /**
     * 分页查询禁言记录
     *
     * @param bid       bot QQ 账号
     * @param groupId   群号，0 表示不过滤
     * @param startTime 开始时间戳（ms），0 表示不过滤
     * @param endTime   结束时间戳（ms），0 表示不过滤
     * @param page      页码，从 1 开始
     * @param size      每页大小
     * @return 记录列表，每条为 Map（id/group_id/target_id/operator_id/duration/time）
     */
    public List<Map<String, Object>> queryMute(long bid, long groupId, long startTime, long endTime, int page, int size) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(groupId, startTime, endTime, params);
        params.add(size);
        params.add((page - 1) * size);
        String sql = "SELECT id, group_id, target_id, operator_id, duration, time FROM mute_record"
                + where + " ORDER BY time DESC LIMIT ? OFFSET ?";
        return getTemplate(bid).queryForList(sql, params.toArray());
    }

    /**
     * 统计禁言记录总数
     *
     * @param bid       bot QQ 账号
     * @param groupId   群号，0 表示不过滤
     * @param startTime 开始时间戳（ms），0 表示不过滤
     * @param endTime   结束时间戳（ms），0 表示不过滤
     * @return 符合条件的记录总数
     */
    public long countMute(long bid, long groupId, long startTime, long endTime) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(groupId, startTime, endTime, params);
        String sql = "SELECT COUNT(*) FROM mute_record" + where;
        Long cnt = getTemplate(bid).queryForObject(sql, Long.class, params.toArray());
        return cnt == null ? 0L : cnt;
    }

    /**
     * 查询指定时间范围内踢人次数最多的操作者排行
     *
     * @param bid       bot QQ 账号
     * @param groupId   群号，0 表示不过滤
     * @param startTime 开始时间戳（ms），0 表示不过滤
     * @param endTime   结束时间戳（ms），0 表示不过滤
     * @param limit     返回条数上限
     * @return 列表，每条含 operator_id 和 cnt（操作次数），按 cnt 降序
     */
    public List<Map<String, Object>> topKickOperators(long bid, long groupId, long startTime, long endTime, int limit) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(groupId, startTime, endTime, params);
        params.add(limit);
        String sql = "SELECT operator_id, COUNT(*) AS cnt FROM kick_record"
                + where + " GROUP BY operator_id ORDER BY cnt DESC LIMIT ?";
        return getTemplate(bid).queryForList(sql, params.toArray());
    }

    /**
     * 查询指定时间范围内禁言次数最多的操作者排行
     *
     * @param bid       bot QQ 账号
     * @param groupId   群号，0 表示不过滤
     * @param startTime 开始时间戳（ms），0 表示不过滤
     * @param endTime   结束时间戳（ms），0 表示不过滤
     * @param limit     返回条数上限
     * @return 列表，每条含 operator_id 和 cnt（操作次数），按 cnt 降序
     */
    public List<Map<String, Object>> topMuteOperators(long bid, long groupId, long startTime, long endTime, int limit) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(groupId, startTime, endTime, params);
        params.add(limit);
        String sql = "SELECT operator_id, COUNT(*) AS cnt FROM mute_record"
                + where + " GROUP BY operator_id ORDER BY cnt DESC LIMIT ?";
        return getTemplate(bid).queryForList(sql, params.toArray());
    }
}
