package io.github.gdpl2112.dg_bot;

import io.github.gdpl2112.dg_bot.utils.HttpsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static io.github.gdpl2112.dg_bot.compile.CompileRes.getCompileTime;

/**
 * @author github-kloping
 * @since 2023-07-17
 */
@SpringBootApplication(exclude = {OpenAiAudioSpeechAutoConfiguration.class})
@EnableAsync
@CrossOrigin
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@EnableScheduling
@Slf4j
public class DgMain implements CommandLineRunner {

    public static ConfigurableApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 该包下所有类，会自动创建表
    private Class<?>[] DbClasses = {
            io.github.gdpl2112.dg_bot.dao.Administrator.class,
            io.github.gdpl2112.dg_bot.dao.AllMessage.class,
            io.github.gdpl2112.dg_bot.dao.AuthM.class,
            io.github.gdpl2112.dg_bot.dao.CallTemplate.class,
            io.github.gdpl2112.dg_bot.dao.Conf.class,
            io.github.gdpl2112.dg_bot.dao.AiConf.class,
            io.github.gdpl2112.dg_bot.dao.ConnConfig.class,
            io.github.gdpl2112.dg_bot.dao.CronMessage.class,
            io.github.gdpl2112.dg_bot.dao.GroupConf.class,
            io.github.gdpl2112.dg_bot.dao.LikeReco.class,
            io.github.gdpl2112.dg_bot.dao.Optional.class,
            io.github.gdpl2112.dg_bot.dao.Passive.class,
            io.github.gdpl2112.dg_bot.dao.Statistics.class,
            io.github.gdpl2112.dg_bot.dao.V11Conf.class
    };

    public static void main(String[] args) throws Exception {
        System.out.println("start pre build time on " + getCompileTime());
        HttpsUtils.trustAllHttpsCertificates();
        // System.getProperty("overflow.timeout")?.toLongOrNull() ?: 10000L
        //System.getProperty("overflow.timeout-process")?.toLongOrNull() ?: 20000L
        System.setProperty("overflow.timeout-process", "180000");
        System.setProperty("overflow.timeout", "180000");
        System.setProperty("overflow.skip-token-security-check", "I_KNOW_WHAT_I_AM_DOING");
        applicationContext = SpringApplication.run(DgMain.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        for (Class<?> dclass : DbClasses) {
            String sql = Utils.CreateTable.createTable(dclass);
            try {
                int state = jdbcTemplate.update(sql);
                if (state > 0) System.out.println(sql);
            } catch (Exception e) {
                log.error("update sql error: {}", sql, e);
            }
        }
        log.info("tables create finished");
        log.info("tables update");

        boolean k0 = false;
        for (Map<String, Object> e0 : jdbcTemplate.queryForList("pragma table_info ('v11_conf')")) {
            String name = e0.get("name").toString();
            if ("like_black".equals(name)) {
                k0 = true;
            }
        }
        if (!k0) {
            System.out.println("v11_conf添加字段");
            jdbcTemplate.update("alter table v11_conf add like_black VARCHAR(255) default '';");
        }
        k0 = false;
        for (Map<String, Object> e0 : jdbcTemplate.queryForList("pragma table_info ('v11_conf')")) {
            String name = e0.get("name").toString();
            if ("like_white".equals(name)) {
                k0 = true;
            }
        }
        if (!k0) {
            System.out.println("v11_conf添加字段");
            jdbcTemplate.update("alter table v11_conf add like_white VARCHAR(255) default '';");
        }
        k0 = false;
        for (Map<String, Object> e0 : jdbcTemplate.queryForList("pragma table_info ('v11_conf')")) {
            String name = e0.get("name").toString();
            if ("zone_walks".equals(name)) {
                k0 = true;
            }
        }
        if (!k0) {
            System.out.println("v11_conf添加字段");
            jdbcTemplate.update("alter table v11_conf add zone_walks VARCHAR(255) default '';");
        }
        k0 = false;
        for (Map<String, Object> e0 : jdbcTemplate.queryForList("pragma table_info ('v11_conf')")) {
            String name = e0.get("name").toString();
            if ("zone_evl".equals(name)) {
                k0 = true;
            }
        }
        if (!k0) {
            System.out.println("v11_conf添加字段");
            jdbcTemplate.update("alter table v11_conf add zone_evl INTEGER default 10;");
        }
        k0 = false;
        for (Map<String, Object> e0 : jdbcTemplate.queryForList("pragma table_info ('group_conf')")) {
            String name = e0.get("name").toString();
            if ("k4".equals(name)) {
                k0 = true;
            }
        }
        if (!k0) {
            System.out.println("group_conf添加字段k4");
            jdbcTemplate.update("alter table group_conf add k4 INTEGER default 0;");
        }
        k0 = false;

        // optional 表添加 tid 字段（内置功能按群配置）
        k0 = false;
        for (Map<String, Object> e0 : jdbcTemplate.queryForList("pragma table_info ('optional')")) {
            String name = e0.get("name").toString();
            if ("tid".equals(name)) {
                k0 = true;
            }
        }
        if (!k0) {
            log.info("optional表添加tid字段");
            jdbcTemplate.update("alter table optional add tid VARCHAR(255) default '*';");
            // 将旧数据中 open=1 的记录设置为 tid='*'（全局启用）
            // 旧数据 open=0 的保持默认 '*' 但标记为不启用
            log.info("迁移旧optional数据：为已有记录设置tid='*'");
        }
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
