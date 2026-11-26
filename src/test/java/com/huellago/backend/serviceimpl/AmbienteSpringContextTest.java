package com.huellago.backend.serviceimpl;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AmbienteSpringContextTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(AmbienteConfiguration.class, AmbienteServiceImpl.class)
            .withPropertyValues(
                    "huellago.ambiente.open-meteo.url=https://air-quality-api.open-meteo.com/v1/air-quality",
                    "huellago.ambiente.geoapify.url=https://api.geoapify.com/v2/places",
                    "huellago.ambiente.open-meteo.enabled=false",
                    "huellago.ambiente.geoapify.enabled=true",
                    "huellago.ambiente.geoapify.api-key=");

    @Test void springInyectaSuMapperJackson3EnElServicioRealSinBaseNiRed() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(JsonMapper.class);
            assertThat(ctx.getBeansOfType(com.fasterxml.jackson.databind.ObjectMapper.class)).isEmpty();
            assertThat(ctx).hasSingleBean(AmbienteServiceImpl.class);
            assertThat(ctx.getBean(AmbienteServiceImpl.class)).isNotNull();
            assertThat(ctx).doesNotHaveBean(DataSource.class);
        });
    }
}
