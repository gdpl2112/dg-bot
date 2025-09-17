package io.github.gdpl2112.dg_bot.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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
    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.sessionManagement()
                .and()
                .formLogin().disable()
                .httpBasic().disable()
                //=============
                .authorizeRequests()
                .antMatchers(
                        "/api/bot/list",
                        "/api/bot/alist",
                        "/api/bot/avatar",
                        "/api/rec","/api/rec/test"
                )
                .permitAll()
                .anyRequest()
                .authenticated()
                .and()
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.getWriter().write("Access Denied: Insufficient permissions");
                        })
                )
                //========
                .rememberMe()
                .tokenValiditySeconds(60 * 60 * 24)
                .and()
                .csrf().disable();

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