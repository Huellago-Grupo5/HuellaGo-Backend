package com.huellago.backend.serviceimpl;

import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.services.RetoColectivoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CierreRetoColectivoCondicionalTest {
    // Contexto limitado: sin autoconfiguracion, datasource ni scheduler.
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(CierreRetoColectivoProgramado.class)
            .withBean(RetoRepository.class, () -> mock(RetoRepository.class))
            .withBean(RetoColectivoService.class, () -> mock(RetoColectivoService.class));

    @Test
    void noRegistraComponenteCuandoEstaDeshabilitado() {
        contexto.withPropertyValues("huellago.cierre-colectivo.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(CierreRetoColectivoProgramado.class);
            verifyNoInteractions(ctx.getBean(RetoRepository.class), ctx.getBean(RetoColectivoService.class));
        });
    }

    @Test
    void registraComponenteCuandoEstaHabilitado() {
        contexto.withPropertyValues("huellago.cierre-colectivo.enabled=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(CierreRetoColectivoProgramado.class);
            verifyNoInteractions(ctx.getBean(RetoRepository.class), ctx.getBean(RetoColectivoService.class));
        });
    }

    @Test
    void registraComponentePorDefecto() {
        contexto.run(ctx -> {
            assertThat(ctx).hasSingleBean(CierreRetoColectivoProgramado.class);
            verifyNoInteractions(ctx.getBean(RetoRepository.class), ctx.getBean(RetoColectivoService.class));
        });
    }
}
