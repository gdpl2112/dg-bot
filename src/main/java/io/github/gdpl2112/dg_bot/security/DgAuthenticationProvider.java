package io.github.gdpl2112.dg_bot.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

public class DgAuthenticationProvider implements AuthenticationProvider {
    public static final Map<String, String> EID2TOKEN = new HashMap<>();

    public static final String FORM_CODE_KEY = "code";
    public static final String FORM_STATE_KEY = "state";
    public static final String SESSION_CODE_KEY = FORM_CODE_KEY;
    public static final String SESSION_STATE_KEY = FORM_STATE_KEY;

    private UserDetailsService userDetailsService;

    public DgAuthenticationProvider(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        authenticationChecks(authentication);
        DgAuthenticationToken authDao = (DgAuthenticationToken) authentication;
        UserDetails userDetails = userDetailsService.loadUserByUsername(authDao.getPrincipal().toString());
        DgAuthenticationToken result = new DgAuthenticationToken(userDetails, userDetails.getPassword(), userDetails.getAuthorities());
        result.setDetails(authDao.getDetails());
        return result;
    }

    /**
     * 认证信息校验
     *
     * @param authentication
     */
    private void authenticationChecks(Authentication authentication) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        DgAuthenticationToken githubCodeAuthenticationToken = (DgAuthenticationToken) authentication;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return (DgAuthenticationToken.class.isAssignableFrom(authentication));
    }
}
