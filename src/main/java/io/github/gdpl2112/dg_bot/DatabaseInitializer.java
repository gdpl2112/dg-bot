package io.github.gdpl2112.dg_bot;

import io.github.gdpl2112.dg_bot.dao.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 数据库表初始化管理器。
 * 负责：自动建表、字段迁移补齐。
 *
 * @author github-kloping
 */
@Slf4j
@Component
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 该包下所有 DAO 类，启动时自动创建对应数据表
     */
    private static final Class<?>[] DB_CLASSES = {
            Administrator.class,
            AllMessage.class,
            AuthM.class,
            CallTemplate.class,
            Conf.class,
            AiConf.class,
            ConnConfig.class,
            CronMessage.class,
            GroupConf.class,
            LikeReco.class,
            Optional.class,
            Passive.class,
            Statistics.class,
            V11Conf.class
    };

    @Override
    public void run(String... args) {
        autoCreateTables();
        migrateV11ConfFields();
        migrateGroupConfFields();
        migrateOptionalFields();
        log.info("数据库表初始化完成");
    }

    // ==================== 自动建表 ====================

    private void autoCreateTables() {
        for (Class<?> dclass : DB_CLASSES) {
            try {
                String sql = Utils.CreateTable.createTable(dclass);
                int state = jdbcTemplate.update(sql);
                if (state > 0) log.debug("建表/更新: {}", dclass.getSimpleName());
            } catch (Exception e) {
                log.error("建表失败: {}", dclass.getSimpleName(), e);
            }
        }
        log.info("所有表已检查/创建完毕");
    }

    // ==================== v11_conf 字段迁移 ====================

    private void migrateV11ConfFields() {
        addColumnIfAbsent("v11_conf", "like_black", "VARCHAR(255) default ''");
        addColumnIfAbsent("v11_conf", "like_white", "VARCHAR(255) default ''");
        addColumnIfAbsent("v11_conf", "zone_walks", "VARCHAR(255) default ''");
        addColumnIfAbsent("v11_conf", "zone_evl", "INTEGER default 10");
    }

    // ==================== group_conf 字段迁移 ====================

    private void migrateGroupConfFields() {
        addColumnIfAbsent("group_conf", "k4", "INTEGER default 0");
    }

    // ==================== optional 字段迁移 ====================

    private void migrateOptionalFields() {
        if (addColumnIfAbsent("optional", "tid", "VARCHAR(255) default '*'")) {
            log.info("迁移旧optional数据：为已有记录设置tid='*'");
        }
    }

    // ==================== 通用工具方法 ====================

    /**
     * 检查表中是否存在指定列，不存在则添加。
     *
     * @param tableName  表名
     * @param columnName 列名
     * @param columnDef  列定义（如 "INTEGER default 0"）
     * @return true 表示新增了列
     */
    private boolean addColumnIfAbsent(String tableName, String columnName, String columnDef) {
        try {
            for (Map<String, Object> row : jdbcTemplate.queryForList("pragma table_info ('" + tableName + "')")) {
                if (columnName.equals(row.get("name").toString())) {
                    return false; // 已存在，无需添加
                }
            }
            String sql = "alter table " + tableName + " add " + columnName + " " + columnDef + ";";
            jdbcTemplate.update(sql);
            log.info("表 {} 添加字段 {}", tableName, columnName);
            return true;
        } catch (Exception e) {
            log.error("为表 {} 添加字段 {} 失败", tableName, columnName, e);
            return false;
        }
    }
}
