package io.github.gdpl2112.dg_bot;

import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.text.SimpleDateFormat;

/**
 * @author github-kloping
 * @since 2023-07-17
 */
@Configuration
public class BaseComponent {
    public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy年MM月dd日");
    @Autowired
    AuthMapper authMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    @Scheduled(cron = "0 1 1 * * ? ")
    public void auth() {
        for (AuthM authM : authMapper.selectList(null)) {
            if (authM.getExp() < System.currentTimeMillis()) {
                Long qid = Long.valueOf(authM.getQid());
                Bot bot = Bot.getInstanceOrNull(qid);
                if (bot != null && bot.isOnline()) bot.close();
            }
        }
    }
}
