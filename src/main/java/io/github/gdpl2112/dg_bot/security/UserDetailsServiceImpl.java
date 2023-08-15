package io.github.gdpl2112.dg_bot.security;

import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.kloping.judge.Judge;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${super.qid:3474006766}")
    String superQid;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthM temp = null;
        temp = authMapper.selectById(username);
        if (temp == null) {
            return null;
        }
        String id = temp.getQid();
        User.UserBuilder builder = User.builder();
        builder.username(id).password(passwordEncoder.encode(temp.getAuth()));
        if (Judge.isNotEmpty(superQid)) {
            if (id.equals(superQid))
                builder.authorities("admin", "user");
            else
                builder.authorities(AuthorityUtils.commaSeparatedStringToAuthorityList("user"));
        } else {
            builder.authorities(AuthorityUtils.commaSeparatedStringToAuthorityList("user"));
        }


        return builder.build();
    }
}