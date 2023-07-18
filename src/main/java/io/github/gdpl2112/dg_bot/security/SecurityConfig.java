package io.github.gdpl2112.dg_bot.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    private static final Set<String> NEED_AUTH_PAGES = new CopyOnWriteArraySet<>();

    static {
        NEED_AUTH_PAGES.add("/bot.html");
        NEED_AUTH_PAGES.add("/user");
    }

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
//        http.formLogin().loginPage("/login.html").loginProcessingUrl("/login")
//                .failureForwardUrl("/fail").failureUrl("/login.html?tips=error").defaultSuccessUrl("/");
//
//        http.logout().logoutUrl("/logout");

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

        DgAuthenticationProcessingFilter dgFilter = new DgAuthenticationProcessingFilter();

        dgFilter.setAuthenticationManager(authenticationManagerBean());
        dgFilter.setAuthenticationSuccessHandler(new DgAuthenticationSuccessHandler());
        dgFilter.setAuthenticationFailureHandler(new DgAuthenticationFailureHandler());

        DgAuthenticationProvider dgProvider = new DgAuthenticationProvider(userDetailsService);

        http.authenticationProvider(dgProvider).addFilterAfter(dgFilter, UsernamePasswordAuthenticationFilter.class);
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        super.configure(web);
    }
}