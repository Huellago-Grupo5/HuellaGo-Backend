package com.huellago.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class LimiteRecuperacionConfiguration {
    @Bean("recoveryRateClock")
    Clock recoveryRateClock() { return Clock.systemUTC(); }
}
