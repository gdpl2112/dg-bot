package io.github.gdpl2112.dg_bot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    private static final Set<String> NEED_AUTH_PAGES = new CopyOnWriteArraySet<>();

    static {
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.formLogin().loginPage("/login.html")
                .loginProcessingUrl("/login")
                .failureForwardUrl("/fail")
                .failureUrl("/login.html?tips=error")
                .defaultSuccessUrl("/");

        http.logout().logoutUrl("/logout");

        http.authorizeRequests()
                //匹配这些地址
                .mvcMatchers(NEED_AUTH_PAGES.toArray(new String[0]))
                //需要认证
                .authenticated()
                //其他的
                .anyRequest()
                //全部放行
                .permitAll();

        http.csrf().disable();

    }


    @Override
    public void configure(WebSecurity web) throws Exception {
        super.configure(web);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}