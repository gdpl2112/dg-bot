package io.github.gdpl2112.dg_bot.security;

import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;

/**
 * 登录过滤器
 * 拦截 /bot/login 请求，验证用户凭证后生成内存令牌
 * 支持GET和POST方式登录，凭证通过后以Cookie和响应体返回令牌
 */
@Slf4j
public class LoginFilter extends OncePerRequestFilter {

    /** Cookie名称，与TokenAuthFilter保持一致 */
    private static final String TOKEN_COOKIE = "dg-token";
    /** Cookie有效期：2小时（秒） */
    private static final int COOKIE_MAX_AGE = 2 * 60 * 60;

    private final TokenStore tokenStore;
    private final AuthMapper authMapper;

    /**
     * @param tokenStore  令牌存储
     * @param authMapper  认证数据Mapper
     */
    public LoginFilter(TokenStore tokenStore, AuthMapper authMapper) {
        this.tokenStore = tokenStore;
        this.authMapper = authMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // 仅拦截 /bot/login
        return !"/bot/login".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        if (!"GET".equals(method) && !"POST".equals(method)) {
            response.setContentType("text/plain;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("不可用的请求方法");
            return;
        }

        String qid = getQid(request);
        String p = getP(request);

        if (qid == null || qid.isEmpty() || p == null || p.isEmpty()) {
            response.setContentType("text/plain;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("认证失败\n参数错误!");
            return;
        }

        // 直接查库验证凭证，不经过Spring AuthenticationManager
        AuthM authM = authMapper.selectById(qid);
        if (authM == null || !p.equals(authM.getAuth())) {
            response.setContentType("text/plain;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("认证失败\n请使用正确的登录链接或授权码/密码!");
            return;
        }

        // 生成令牌（单点：自动踢掉该用户的旧令牌）
        String token = tokenStore.createToken(qid);
        log.info("用户 {} 登录成功，令牌已生成", qid);

        // 设置SecurityContext，使后续过滤器识别为已认证
        UserDetails userDetails = User.builder()
                .username(qid)
                .password(authM.getAuth())
                .authorities(AuthorityUtils.commaSeparatedStringToAuthorityList("user"))
                .build();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 将令牌写入Cookie
        Cookie cookie = new Cookie(TOKEN_COOKIE, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        response.addCookie(cookie);

        // 根据请求方式返回不同响应
        if ("GET".equals(method)) {
            response.sendRedirect("/bot");
        } else {
            response.setContentType("text/plain;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("授权码/密码 正确");
        }
    }

    /**
     * 从请求中提取用户ID（qid参数）
     */
    private String getQid(HttpServletRequest request) throws IOException {
        String qid = request.getParameter("qid");
        if (qid != null) return qid;
        String json = readJsonFromRequest(request);
        return parseJsonField(json, "qid");
    }

    /**
     * 从请求中提取密码/授权码（p参数）
     */
    private String getP(HttpServletRequest request) throws IOException {
        String p = request.getParameter("p");
        if (p != null) return p;
        String json = readJsonFromRequest(request);
        return parseJsonField(json, "p");
    }

    /**
     * 读取请求体JSON
     */
    private String readJsonFromRequest(HttpServletRequest request) throws IOException {
        if (cache0 != null && cache0.getKey() == request) {
            return cache0.getValue();
        }
        StringBuilder jsonBuilder = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
        }
        String body = jsonBuilder.toString();
        cache0 = new AbstractMap.SimpleEntry<>(request, body);
        return body;
    }

    /**
     * 解析JSON字段
     */
    private String parseJsonField(String json, String field) {
        if (json == null || json.isEmpty()) return null;
        try {
            com.alibaba.fastjson.JSONObject jsonObject = com.alibaba.fastjson.JSON.parseObject(json);
            return jsonObject.getString(field);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage());
        }
    }

    /** 缓存请求体，避免重复读取 */
    private Map.Entry<HttpServletRequest, String> cache0 = null;
}
