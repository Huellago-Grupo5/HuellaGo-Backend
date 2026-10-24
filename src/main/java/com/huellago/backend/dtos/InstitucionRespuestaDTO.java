package com.huellago.backend.dtos;

import com.huellago.backend.entities.RolInstitucional;
import com.huellago.backend.entities.TipoInstitucion;
import java.time.LocalDateTime;

public record InstitucionRespuestaDTO(
        Long id, TipoInstitucion tipo, String nombre, String pais,
        String identificadorFiscal, String correoContacto,
        LocalDateTime fechaRegistro, Long representanteId, RolInstitucional rol
) {}
