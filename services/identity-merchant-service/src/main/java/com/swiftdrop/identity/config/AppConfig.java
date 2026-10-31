package com.swiftdrop.identity.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
@EnableScheduling
public class AppConfig {

    /** Injected wherever "now" matters, so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
