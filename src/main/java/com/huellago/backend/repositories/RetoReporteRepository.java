package com.huellago.backend.repositories;

import com.huellago.backend.repositories.projections.CampanaReporteVista;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

public interface RetoReporteRepository {
    Page<CampanaReporteVista> buscarParaReporte(Long institucionId, LocalDate desde, LocalDate hasta,
                                               Integer campanaId, Pageable pagina);
}
