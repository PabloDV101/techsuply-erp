package com.techsupply.erp_backend.security.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityClockConfig {
    @Bean
    Clock securityClock() { return Clock.systemUTC(); }
}
