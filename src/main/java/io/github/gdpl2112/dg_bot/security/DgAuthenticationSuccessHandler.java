package io.github.gdpl2112.dg_bot.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class DgAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (request.getMethod().equalsIgnoreCase("POST")) {
            response.setContentType("text/plain;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("授权码/密码 正确");
        } else if (request.getMethod().equalsIgnoreCase("GET")) {
            response.sendRedirect("/bot");
        } else {
            response.setContentType("text/plain;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("不可用的请求方法");
        }
    }
}
