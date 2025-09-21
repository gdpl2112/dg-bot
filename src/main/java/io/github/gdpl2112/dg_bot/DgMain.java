package io.github.gdpl2112.dg_bot;

import io.github.kloping.MySpringTool.h1.impl.component.PackageScannerImpl;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.MySpringTool.interfaces.component.PackageScanner;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import static io.github.gdpl2112.dg_bot.compile.CompileRes.getCompileTime;

/**
 * @author github-kloping
 * @since 2023-07-17
 */
@SpringBootApplication
@EnableAsync
@CrossOrigin
@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
@EnableScheduling
public class DgMain implements CommandLineRunner {

    public static ConfigurableApplicationContext applicationContext;

    public static void main(String[] args) throws Exception {
        System.out.println("start pre build time on " + getCompileTime());
        HttpsUtils.trustAllHttpsCertificates();
        System.setProperty("overflow.skip-token-security-check","I_KNOW_WHAT_I_AM_DOING");
        applicationContext = SpringApplication.run(DgMain.class, args);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    Logger logger;

    @Override
    public void run(String... args) throws Exception {
        logger.info("start auto create need tables;");
        PackageScanner scanner = new PackageScannerImpl(true);
        for (Class<?> dclass : scanner.scan(DgMain.class, DgMain.class.getClassLoader(), "io.github.gdpl2112.dg_bot.dao")) {
            String sql = Utils.CreateTable.createTable(dclass);
            try {
                int state = jdbcTemplate.update(sql);
                if (state > 0) System.out.println(sql);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error(sql);
            }
        }
        logger.info("tables create finished");

        logger.info("tables update");

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
    }

    /**
     * 解决异常信息：
     * java.lang.IllegalArgumentException:
     * Invalid character found in the request target. The valid characters are defined in RFC 7230 and RFC 3986
     *
     * @return
     */
    @Bean
    public ConfigurableServletWebServerFactory webServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addConnectorCustomizers(new TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                connector.setProperty("relaxedQueryChars", "//\\|{}[]");
            }
        });
        return factory;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
