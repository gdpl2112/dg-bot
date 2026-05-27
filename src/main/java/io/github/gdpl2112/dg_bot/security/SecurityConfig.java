package io.github.gdpl2112.dg_bot.security;

import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 基于内存令牌的认证配置
 * 使用自定义过滤器（LoginFilter + TokenAuthFilter）替代Spring内置认证
 * 令牌存储在内存中（userId -> token），有效期2小时，单点登录
 */
@Configuration
public class SecurityConfig {

    @Autowired
    private AuthMapper authMapper;

    @Value("${super.qid:3474006766}")
    private String superQid;

    @Value("${manage.key}")
    private String manageKey;

    @Value("${manage.bid}")
    private String manageBid;

    /**
     * 内存令牌存储，单例Bean
     *
     * @return TokenStore实例
     */
    @Bean
    public TokenStore tokenStore() {
        return new TokenStore();
    }

    /**
     * 登录过滤器，拦截 /bot/login 验证凭证并生成令牌
     *
     * @return LoginFilter实例
     */
    @Bean
    public LoginFilter loginFilter() {
        return new LoginFilter(tokenStore(), authMapper);
    }

    /**
     * 令牌认证过滤器，从Cookie/Header提取令牌验证后设置安全上下文
     *
     * @return TokenAuthFilter实例
     */
    @Bean
    public TokenAuthFilter tokenAuthFilter() {
        return new TokenAuthFilter(tokenStore(), authMapper, superQid);
    }

    /**
     * Manage接口静态密钥过滤器，校验 X-Manage-Key 请求头并授予 manage 权限
     *
     * @return ManageKeyFilter实例
     */
    @Bean
    public ManageKeyFilter manageKeyFilter() {
        return new ManageKeyFilter(manageKey, manageBid);
    }

    /**
     * 构建安全过滤链
     * 不使用Spring内置认证（AuthenticationManager、RememberMe、SessionRegistry），
     * 完全由自定义过滤器实现登录认证、令牌校验和单点控制
     *
     * @param http HTTP安全配置构建器
     * @return 安全过滤链
     * @throws Exception 配置过滤链时可能抛出的异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .authorizeHttpRequests(authz -> authz
                    .requestMatchers(
                            "/api/bot/list",
                            "/api/bot/alist",
                            "/api/bot/avatar",
                            "/api/pre/statistics",
                            "/api/rec",
                            "/bot/login",
                            "/api/rec/test"
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
                    .addLogoutHandler((request, response, authentication) -> {
                        // 从Cookie中提取令牌并从内存中移除
                        if (request.getCookies() != null) {
                            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                                if ("dg-token".equals(cookie.getName())) {
                                    String token = cookie.getValue();
                                    String userId = tokenStore().validateToken(token);
                                    if (userId != null) {
                                        tokenStore().removeToken(userId);
                                    }
                                }
                            }
                        }
                    })
                    .deleteCookies("dg-token", "JSESSIONID")
                    .clearAuthentication(true)
                    .logoutSuccessHandler((request, response, authentication) -> {
                        response.setContentType("text/plain;charset=UTF-8");
                        response.setStatus(HttpServletResponse.SC_OK);
                        response.getWriter().write("已登出");
                    })
            )
            // 禁用Session管理，不再依赖Spring内置会话控制
            .sessionManagement(session -> session.disable())
            .csrf(csrf -> csrf.disable())
            // 先执行令牌认证过滤器，再执行登录过滤器；manage密钥过滤器在令牌过滤器之后运行，可覆盖安全上下文
            .addFilterBefore(tokenAuthFilter(),
                    org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(loginFilter(),
                    org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(manageKeyFilter(), TokenAuthFilter.class);

        return http.build();
    }
}