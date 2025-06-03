package io.github.gdpl2112.dg_bot.security;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;


public class DgAuthenticationProcessingFilter extends AbstractAuthenticationProcessingFilter {

    private static final AntPathRequestMatcher DEFAULT_ANT_PATH_REQUEST_MATCHER = new AntPathRequestMatcher("/bot/login", "GET");

    public DgAuthenticationProcessingFilter() {
        super(new OrRequestMatcher(
                new AntPathRequestMatcher("/bot/login", "GET"),
                new AntPathRequestMatcher("/bot/login", "POST")
        ));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {
        if (!request.getMethod().equals("GET") && !request.getMethod().equals("POST")) {
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

    private String getQid(HttpServletRequest request) throws IOException {
        String qid = request.getParameter("qid");
        if (qid != null) return qid;
        String json = readJsonFromRequest(request);
        return parseJsonField(json, "qid");
    }

    private String getP(HttpServletRequest request) throws IOException {
        String qid = request.getParameter("p");
        if (qid != null) return qid;
        String json = readJsonFromRequest(request);
        return parseJsonField(json, "p");
    }

    private Map.Entry<HttpServletRequest, String> cache0 = null;

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

    private String parseJsonField(String json, String field) {
        if (json == null || json.isEmpty()) return null;
        try {
            com.alibaba.fastjson.JSONObject jsonObject = com.alibaba.fastjson.JSON.parseObject(json);
            return jsonObject.getString(field);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage());
        }
    }

    protected void setDetails(HttpServletRequest request, DgAuthenticationToken authRequest) {
        authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));
    }
}
