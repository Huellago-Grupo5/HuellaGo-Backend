package com.huellago.backend.services;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.dtos.DefinicionPresentacionIADTO;
import java.util.*;

/** Filtro interno previo a selección. No selecciona, infiere, persiste ni publica. */
public interface PoliticaAutorizacionEditorialIAService {
    Resultado evaluar(ContextoRecomendacionesIADTO contexto, Set<AccionAmbientalAdmisible.Referencia> realizadas);
    String huellaDefinicion(DefinicionPresentacionIADTO definicion);

    /** Fuente confiable de composición interna; nunca un argumento de una petición HTTP.
     * No existe implementación productiva con permisos precargados. */
    @FunctionalInterface interface Registro {
        List<Autorizacion> consultar();
    }
    record Autorizacion(String accionId, String versionAccion, String enfoque, String versionPresentacion,
                        String sha256, Set<String> camposContextoPermitidos) {
        public Autorizacion { camposContextoPermitidos = Set.copyOf(camposContextoPermitidos); }
    }
    enum MotivoExclusion { SIN_AUTORIZACION, FAMILIA_INCOMPLETA, AUTORIZACION_OBSOLETA,
        DEFINICION_NO_COINCIDE, FAMILIA_NO_VERIFICABLE, REGISTRO_INVALIDO }
    record Resultado(List<CatalogoAccionesAmbientalesService.Candidata> compatibles,
                     List<CatalogoAccionesAmbientalesService.Candidata> elegibles,
                     Map<AccionAmbientalAdmisible.Referencia, MotivoExclusion> excluidas) {
        public Resultado {
            compatibles = List.copyOf(compatibles); elegibles = List.copyOf(elegibles); excluidas = Map.copyOf(excluidas);
        }
        public boolean publicable() { return false; }
    }
}
