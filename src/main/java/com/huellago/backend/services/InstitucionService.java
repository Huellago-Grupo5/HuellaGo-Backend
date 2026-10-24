package com.huellago.backend.services;

import com.huellago.backend.dtos.InstitucionRegistroDTO;
import com.huellago.backend.dtos.InstitucionRespuestaDTO;

public interface InstitucionService {
    InstitucionRespuestaDTO registrar(Long usuarioId, InstitucionRegistroDTO datos);
    InstitucionRespuestaDTO consultarAdministracion(Long usuarioId, Long institucionId);
}
