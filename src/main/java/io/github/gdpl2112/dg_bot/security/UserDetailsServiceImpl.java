package io.github.gdpl2112.dg_bot.security;

import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * @author github-kloping
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    final PasswordEncoder passwordEncoder;

    final AuthMapper authMapper;

    public UserDetailsServiceImpl(AuthMapper authMapper, PasswordEncoder passwordEncoder) {
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthM temp = null;
        temp = authMapper.selectById(username);
        if (temp == null) {
            return null;
        }
        String id = temp.getQid();
        return new User(id, passwordEncoder.encode(temp.getAuth()),
                AuthorityUtils.commaSeparatedStringToAuthorityList("user"));
    }
}