package com.huellago.backend.services;

import com.huellago.backend.dtos.InstitucionRegistroDTO;
import com.huellago.backend.dtos.InstitucionRespuestaDTO;
import com.huellago.backend.dtos.RankingInstitucionalDTO;

public interface InstitucionService {
    InstitucionRespuestaDTO registrar(Long usuarioId, InstitucionRegistroDTO datos);
    InstitucionRespuestaDTO consultarAdministracion(Long usuarioId, Long institucionId);
    RankingInstitucionalDTO consultarRanking(Long usuarioId, Long institucionId);
}
