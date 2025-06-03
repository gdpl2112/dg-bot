package io.github.gdpl2112.dg_bot.security;

import io.github.kloping.judge.Judge;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public class DgAuthenticationProvider implements AuthenticationProvider {
    private UserDetailsService userDetailsService;

    public DgAuthenticationProvider(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        DgAuthenticationToken authDao = (DgAuthenticationToken) authentication;
        UserDetails userDetails = userDetailsService.loadUserByUsername(authDao.getPrincipal().toString());
        authenticationChecks(userDetails, authDao);
        DgAuthenticationToken result = new DgAuthenticationToken(userDetails, userDetails.getPassword(),
                userDetails.getAuthorities());
        result.setDetails(authDao.getDetails());
        return result;
    }

    /**
     * 认证信息校验
     *
     * @param authentication
     * @param token
     */
    private void authenticationChecks(UserDetails authentication, DgAuthenticationToken token) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        if (Judge.isEmpty(token.getCredentials().toString())) {
            throw new DisabledException("参数错误!");
        } else {
            String u = authentication.getUsername();
            String p = authentication.getPassword();
            if (u.equals(token.getPrincipal()) && p.equals(token.getCredentials())) return;
            else throw new CredentialsExpiredException("请使用正确的登录链接或授权码/密码!");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return (DgAuthenticationToken.class.isAssignableFrom(authentication));
    }
}
