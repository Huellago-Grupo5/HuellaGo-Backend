package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.TipoReto;
import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.services.RetoColectivoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(prefix = "huellago.cierre-colectivo", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CierreRetoColectivoProgramado {
    private static final Logger LOG = LoggerFactory.getLogger(CierreRetoColectivoProgramado.class);
    @Autowired RetoRepository retos;
    @Autowired RetoColectivoService servicio;

    @Scheduled(initialDelay = 10000, fixedDelay = 60000)
    public void cerrarVencidos() {
        for (var reto : retos.findByTipo(TipoReto.COLECTIVO)) {
            if (reto.getFechaFin() != null && LocalDate.now().isAfter(reto.getFechaFin())) {
                try {
                    servicio.cerrarVencido(reto.getId());
                } catch (RuntimeException error) {
                    LOG.error("No se pudo cerrar el reto colectivo {}", reto.getId(), error);
                }
            }
        }
    }
}
