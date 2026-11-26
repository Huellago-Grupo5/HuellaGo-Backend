package com.huellago.backend.serviceimpl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ConsultaHuellaConfiguration {
    @Bean("huellaConsultaClock")
    Clock huellaConsultaClock() {
        return Clock.system(ZoneId.of("America/Lima"));
    }
}
