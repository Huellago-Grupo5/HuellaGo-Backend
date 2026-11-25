package com.huellago.backend.services;

import com.huellago.backend.dtos.*;
import java.util.List;
import java.util.Set;

/** Proyección interna posterior a selección V2: no selecciona, infiere, persiste ni publica. */
public interface PresentacionRecomendacionesIAService {
    /** Vista previa interna sobre contexto validado; no concede aprobacion ni publicacion. */
    ResultadoPresentacionRecomendacionesIADTO presentarConAlcanceInterno(
            ContextoRecomendacionesIADTO contexto, ResultadoGeneracionFundamentadaIADTO seleccion,
            AlcanceSeleccionIADTO alcance, Set<AccionAmbientalAdmisible.Referencia> realizadas);
    /** Definiciones base inmutables, sin composición ni datos personales. No autoriza publicación. */
    List<DefinicionPresentacionIADTO> definicionesParaRevision();

    ResultadoPresentacionRecomendacionesIADTO presentarParaPruebasSinteticas(
            ContextoRecomendacionesIADTO contexto, ResultadoGeneracionFundamentadaIADTO seleccion,
            List<CatalogoAccionesAmbientalesService.Candidata> compatibles,
            Set<AccionAmbientalAdmisible.Referencia> realizadas);

    ResultadoPresentacionRecomendacionesIADTO presentarConAlcanceParaPruebasSinteticas(
            ContextoRecomendacionesIADTO contexto, ResultadoGeneracionFundamentadaIADTO seleccion,
            AlcanceSeleccionIADTO alcance, Set<AccionAmbientalAdmisible.Referencia> realizadas);
}
