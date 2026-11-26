package com.huellago.backend.services;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import java.util.List;
import java.util.Set;

/** Candidatas para una futura selección por IA; no genera ni publica recomendaciones. */
public interface CatalogoAccionesAmbientalesService {
    List<Candidata> listarBorradores();
    List<Candidata> compatibles(ContextoRecomendacionesIADTO contexto, Set<AccionAmbientalAdmisible.Referencia> realizadas);

    enum EstadoRevision { BORRADOR }

    /** Metadatos separados del contrato V2 existente. BORRADOR no equivale a aprobación ambiental. */
    record Candidata(AccionAmbientalAdmisible definicion, String objetivo, String descripcion,
                     List<String> restricciones, EstadoRevision estadoRevision) {
        public Candidata { restricciones = List.copyOf(restricciones); }
    }
}
