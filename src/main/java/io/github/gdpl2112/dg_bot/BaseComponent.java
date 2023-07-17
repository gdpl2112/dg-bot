package io.github.gdpl2112.dg_bot;

import io.github.kloping.MySpringTool.h1.impl.LoggerImpl;
import io.github.kloping.MySpringTool.interfaces.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author github-kloping
 * @date 2023-07-17
 */
@Configuration
public class BaseComponent {
    @Bean
    public Logger getLogger() {
        Logger l = new LoggerImpl();
        String path = String.format(
                "logs/%s.log", new SimpleDateFormat("yyyy/MM-dd").format(new Date()));
        new File(path).getParentFile().mkdirs();
        l.setOutFile(path);
        l.info("================logger=create===================");
        return l;
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }


    @Bean
    public SessionRegistryImpl sessionRegistry() {
        return new SessionRegistryImpl();
    }

}
