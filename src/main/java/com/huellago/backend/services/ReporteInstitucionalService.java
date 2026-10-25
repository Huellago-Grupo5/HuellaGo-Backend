package com.huellago.backend.services;

import com.huellago.backend.dtos.ReporteInstitucionalDTO;
import com.huellago.backend.entities.Usuario;
import java.time.LocalDate;

public interface ReporteInstitucionalService {
    ReporteInstitucionalDTO consultar(Long institucionId, Usuario usuario,
                                      LocalDate desde, LocalDate hasta, Integer campanaId);
}
