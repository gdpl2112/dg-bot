package io.github.gdpl2112.dg_bot.security;

import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.server.ErrorPageRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
public class ErrorPageConfig {

    @Bean
    public ErrorPageRegistrar errorPageRegistrar() {
        return registry -> {
            // 将 404 错误重定向到 /index.html
            registry.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/index.html"));
        };
    }
}