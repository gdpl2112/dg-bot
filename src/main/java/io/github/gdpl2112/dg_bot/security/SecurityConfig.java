package io.github.gdpl2112.dg_bot.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {
    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Bean
    public AuthenticationManager authenticationManager() {
        DgAuthenticationProvider dgProvider = new DgAuthenticationProvider(userDetailsService);
        return new ProviderManager(dgProvider);
    }

    @Bean
    public RememberMeServices rememberMeServices() {
        TokenBasedRememberMeServices rememberMeServices = new TokenBasedRememberMeServices("dg-bot-key", userDetailsService);
        rememberMeServices.setTokenValiditySeconds(60 * 60 * 24);
        rememberMeServices.setAlwaysRemember(true);
        return rememberMeServices;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        DgAuthenticationProcessingFilter dgFilter = new DgAuthenticationProcessingFilter();

        dgFilter.setAuthenticationManager(authenticationManager());
        dgFilter.setAuthenticationSuccessHandler(new DgAuthenticationSuccessHandler());
        dgFilter.setAuthenticationFailureHandler(new DgAuthenticationFailureHandler());
        dgFilter.setSecurityContextRepository(new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository()
        ));
        dgFilter.setRememberMeServices(rememberMeServices());

        http
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .authorizeHttpRequests(authz -> authz
                    .requestMatchers(
                            "/api/bot/list",
                            "/api/bot/alist",
                            "/api/bot/avatar",
                            "/api/pre/statistics",
                            "/api/rec", "/api/rec/test"
                    ).permitAll()
                    .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                    .accessDeniedHandler((request, response, accessDeniedException) -> {
                        response.setStatus(HttpStatus.FORBIDDEN.value());
                        response.getWriter().write("Access Denied: Insufficient permissions");
                    })
            )
            .logout(logout -> logout
                    .logoutUrl("/bot/logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("remember-me", "JSESSIONID")
                    .clearAuthentication(true)
                    .logoutSuccessHandler((request, response, authentication) -> {
                        response.setContentType("text/plain;charset=UTF-8");
                        response.setStatus(HttpServletResponse.SC_OK);
                        response.getWriter().write("已登出");
                    })
            )
            .sessionManagement(session -> session
                    // 单点登录：同一用户只允许一个活跃会话，新登录踢掉旧会话
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
            )
            .rememberMe(remember -> remember
                    .rememberMeServices(rememberMeServices())
            )
            .csrf(csrf -> csrf.disable())
            .addFilterAfter(dgFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}