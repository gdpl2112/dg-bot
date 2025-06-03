package io.github.gdpl2112.dg_bot.security;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


public class DgAuthenticationProcessingFilter extends AbstractAuthenticationProcessingFilter {

    private static final AntPathRequestMatcher DEFAULT_ANT_PATH_REQUEST_MATCHER = new AntPathRequestMatcher("/bot/login", "GET");

    public DgAuthenticationProcessingFilter() {
        super(DEFAULT_ANT_PATH_REQUEST_MATCHER);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {
        if (!request.getMethod().equals("GET")) {
            throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
        }
        String qid = getQid(request);
        qid = (qid != null) ? qid : "";
        qid = qid.trim();
        String auth = getP(request);
        auth = (auth != null) ? auth : "";

        DgAuthenticationToken atoken = null;
        try {
            atoken = new DgAuthenticationToken(qid, auth);
        } catch (Exception e) {
            e.printStackTrace();
        }
        setDetails(request, atoken);
        return this.getAuthenticationManager().authenticate(atoken);
    }

    private String getQid(HttpServletRequest request) {
        return request.getParameter("qid");
    }

    private String getP(HttpServletRequest request) {
        return request.getParameter("p");
    }

    protected void setDetails(HttpServletRequest request, DgAuthenticationToken authRequest) {
        authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));
    }
}
