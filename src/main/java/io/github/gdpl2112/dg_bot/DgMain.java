package io.github.gdpl2112.dg_bot;

import io.github.gdpl2112.dg_bot.utils.HttpsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.client.RestTemplate;

import static io.github.gdpl2112.dg_bot.compile.CompileRes.getCompileTime;

/**
 * Spring Boot 应用主入口。
 * 数据库表初始化逻辑已提取至 {@link DatabaseInitializer}。
 *
 * @author github-kloping
 * @since 2023-07-17
 */
@SpringBootApplication(exclude = {OpenAiAudioSpeechAutoConfiguration.class})
@EnableAsync
@CrossOrigin
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@EnableScheduling
@Slf4j
public class DgMain {

    public static ConfigurableApplicationContext applicationContext;

    public static void main(String[] args) throws Exception {
        System.out.println("start pre build time on " + getCompileTime());
        HttpsUtils.trustAllHttpsCertificates();
        System.setProperty("overflow.timeout-process", "180000");
        System.setProperty("overflow.timeout", "180000");
        System.setProperty("overflow.skip-token-security-check", "I_KNOW_WHAT_I_AM_DOING");
        applicationContext = SpringApplication.run(DgMain.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
