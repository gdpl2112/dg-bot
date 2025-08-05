package io.github.gdpl2112.dg_bot;

import io.github.kloping.MySpringTool.h1.impl.component.PackageScannerImpl;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.MySpringTool.interfaces.component.PackageScanner;
import io.github.kloping.file.FileUtils;
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader;
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

import java.util.Map;

/**
 * @author github-kloping
 * @date 2023-07-17
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
        System.out.println("start pre build time on 25/08.05");
        HttpsUtils.trustAllHttpsCertificates();
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

        //alter table conf
        //    add del0 VARCHAR(120) default '删词';
        //
        //alter table conf
        //    add status0 VARCHAR(120) default '/状态';
        boolean k0 = false;
        for (Map<String, Object> e0 : jdbcTemplate.queryForList("pragma table_info ('conf')")) {
            String name = e0.get("name").toString();
            if ("del0".equals(name)) {
                k0 = true;
            }
        }
        if (!k0) {
            System.out.println("conf添加字段");
            jdbcTemplate.update("alter table conf add del0 VARCHAR(40) default '删词';");
        }
        k0 = false;
        for (Map<String, Object> e0 : jdbcTemplate.queryForList("pragma table_info ('conf')")) {
            String name = e0.get("name").toString();
            if ("status0".equals(name)) {
                k0 = true;
            }
        }
        if (!k0) {
            System.out.println("conf添加字段");
            jdbcTemplate.update("alter table conf add status0 VARCHAR(40) default '/状态';");
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
