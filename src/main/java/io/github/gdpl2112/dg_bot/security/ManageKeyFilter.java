package io.github.gdpl2112.dg_bot.security;

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
 * Manage接口静态密钥认证过滤器
 * 仅拦截 /api/manage/** 请求，从请求头 X-Manage-Key 提取密钥与配置值比对。
 * 验证通过后以 manage.bid 为用户名、manage 为权限写入安全上下文，
 * 替换此前由 TokenAuthFilter 写入的普通用户上下文，从而实现独立权限隔离。
 */
public class ManageKeyFilter extends OncePerRequestFilter {

    /** 密钥请求头名称 */
    private static final String KEY_HEADER = "X-Manage-Key";

    private final String manageKey;
    private final String manageBid;

    /**
     * @param manageKey 配置文件中 manage.key 的值
     * @param manageBid 配置文件中 manage.bid 的值，作为查询数据时的 bot 账号 ID
     */
    public ManageKeyFilter(String manageKey, String manageBid) {
        this.manageKey = manageKey;
        this.manageBid = manageBid;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // 仅处理 /api/manage 及其子路径
        return !path.startsWith("/api/manage");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader(KEY_HEADER);
        if (key != null && key.equals(manageKey)) {
            // 密钥正确：用 manage.bid 作为用户名，授予 manage 权限，覆盖已有安全上下文
            UserDetails userDetails = User.builder()
                    .username(manageBid)
                    .password("")
                    .authorities(AuthorityUtils.commaSeparatedStringToAuthorityList("manage"))
                    .build();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
