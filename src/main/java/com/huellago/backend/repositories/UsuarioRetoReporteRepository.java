package com.huellago.backend.repositories;

import com.huellago.backend.repositories.projections.TotalesParticipacionVista;
import java.time.LocalDate;

public interface UsuarioRetoReporteRepository {
    TotalesParticipacionVista totalesParaReporte(Long institucionId, LocalDate desde, LocalDate hasta, Integer campanaId);
}
