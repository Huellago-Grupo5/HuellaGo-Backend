package com.huellago.backend.dtos;

import com.huellago.backend.entities.RolInstitucional;
import com.huellago.backend.entities.TipoInstitucion;

public record InstitucionAfiliacionDTO(Long id, String nombre, TipoInstitucion tipo,
        RolInstitucional rol, String grupo) {}
