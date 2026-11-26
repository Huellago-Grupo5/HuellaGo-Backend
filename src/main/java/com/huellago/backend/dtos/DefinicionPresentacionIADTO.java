package com.huellago.backend.dtos;

import java.util.Set;

/** Plantilla base para revisión interna; no contiene valores de hábitos ni acredita aprobación. */
public record DefinicionPresentacionIADTO(String accionId, String versionAccion, String enfoque,
        String versionPresentacion, String titulo, String descripcion, String motivo,
        Set<String> camposContextoPermitidos) {
    public DefinicionPresentacionIADTO { camposContextoPermitidos = Set.copyOf(camposContextoPermitidos); }
}
