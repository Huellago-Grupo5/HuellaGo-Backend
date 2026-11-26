package com.huellago.backend.dtos;

import com.huellago.backend.services.AccionAmbientalAdmisible.Referencia;
import com.huellago.backend.services.ContextoRecomendacionesIAService;
import java.util.Set;

/** Lectura interna, nunca credencial ni autorización editorial. No contiene entidades, correo o JWT. */
public record CapturaContextoAmbientalIADTO(String versionHuella, Long usuarioId,
        ContextoRecomendacionesIAService.Resultado contexto, Set<Referencia> realizadas,
        Set<Integer> recomendacionesLegadasRealizadas, Set<Integer> retosLegadosRealizados,
        String huellaVigencia) {
    public CapturaContextoAmbientalIADTO {
        realizadas = Set.copyOf(realizadas);
        recomendacionesLegadasRealizadas = Set.copyOf(recomendacionesLegadasRealizadas);
        retosLegadosRealizados = Set.copyOf(retosLegadosRealizados);
    }
    @Override public String toString() {
        return "CapturaContextoAmbientalIADTO[version=" + versionHuella + ", datos ambientales omitidos]";
    }
}
