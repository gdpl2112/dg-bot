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
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;

import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;

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

    /**
     * 会话注册表，用于跟踪所有活跃会话，支持单点登录的并发控制
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * 监听 HttpSession 生命周期事件，session 销毁时从 SessionRegistry 中移除
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
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
        // 构建并发会话控制策略：限制同一用户最多1个会话，新登录踢掉旧会话
        ConcurrentSessionControlAuthenticationStrategy concurrentStrategy =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry());
        concurrentStrategy.setMaximumSessions(1);
        // false: 新登录踢掉旧会话; true: 阻止新登录
        concurrentStrategy.setExceptionIfMaximumExceeded(false);

        CompositeSessionAuthenticationStrategy sessionStrategy = new CompositeSessionAuthenticationStrategy(
                Arrays.asList(
                        concurrentStrategy,
                        new SessionFixationProtectionStrategy(),
                        new RegisterSessionAuthenticationStrategy(sessionRegistry())
                )
        );

        DgAuthenticationProcessingFilter dgFilter = new DgAuthenticationProcessingFilter();
        dgFilter.setAuthenticationManager(authenticationManager());
        dgFilter.setAuthenticationSuccessHandler(new DgAuthenticationSuccessHandler());
        dgFilter.setAuthenticationFailureHandler(new DgAuthenticationFailureHandler());
        dgFilter.setSecurityContextRepository(new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository()
        ));
        dgFilter.setRememberMeServices(rememberMeServices());
        // 将并发会话策略绑定到自定义过滤器，使单点登录生效
        dgFilter.setSessionAuthenticationStrategy(sessionStrategy);

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
                    .sessionRegistry(sessionRegistry())
            )
            .rememberMe(remember -> remember
                    .rememberMeServices(rememberMeServices())
            )
            .csrf(csrf -> csrf.disable())
            .addFilterAfter(dgFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}