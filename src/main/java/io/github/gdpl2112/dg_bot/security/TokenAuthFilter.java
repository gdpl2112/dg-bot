package io.github.gdpl2112.dg_bot.security;

import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.kloping.judge.Judge;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 令牌认证过滤器
 * 从请求的Cookie或Header中提取令牌，验证有效性后设置Spring Security上下文
 * 不依赖Spring内置的认证机制，完全基于内存令牌校验
 */
public class TokenAuthFilter extends OncePerRequestFilter {

    /** Cookie名称 */
    private static final String TOKEN_COOKIE = "dg-token";
    /** Header名称 */
    private static final String TOKEN_HEADER = "Authorization";

    private final TokenStore tokenStore;
    private final AuthMapper authMapper;
    private final String superQid;

    /**
     * @param tokenStore  令牌存储
     * @param authMapper  认证数据Mapper，用于构建UserDetails
     * @param superQid    超级管理员QQ号
     */
    public TokenAuthFilter(TokenStore tokenStore, AuthMapper authMapper, String superQid) {
        this.tokenStore = tokenStore;
        this.authMapper = authMapper;
        this.superQid = superQid;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            String userId = tokenStore.validateToken(token);
            if (userId != null) {
                UserDetails userDetails = buildUserDetails(userId);
                if (userDetails != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取令牌，优先Cookie，其次Header
     *
     * @param request HTTP请求
     * @return 令牌字符串，不存在则返回null
     */
    private String extractToken(HttpServletRequest request) {
        // 从Cookie中提取
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (TOKEN_COOKIE.equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value != null && !value.isEmpty()) return value;
                }
            }
        }
        // 从Header中提取：Authorization: Bearer <token>
        String header = request.getHeader(TOKEN_HEADER);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    /**
     * 根据用户ID构建UserDetails，用于填充SecurityContext
     *
     * @param userId 用户ID（QQ号）
     * @return UserDetails实例，用户不存在时返回null
     */
    private UserDetails buildUserDetails(String userId) {
        AuthM authM = authMapper.selectById(userId);
        if (authM == null) return null;
        User.UserBuilder builder = User.builder()
                .username(userId)
                .password(authM.getAuth());
        if (Judge.isNotEmpty(superQid) && userId.equals(superQid)) {
            builder.authorities("admin", "user");
        } else {
            builder.authorities(AuthorityUtils.commaSeparatedStringToAuthorityList("user"));
        }
        return builder.build();
    }
}
