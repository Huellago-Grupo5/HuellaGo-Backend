package com.huellago.backend.services;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.dtos.RecomendacionGeneradaIADTO;
import java.util.List;

/** Puerto aislado de inferencia; no consulta ni persiste datos. */
public interface GeneradorRecomendacionesIAService {
    /** Seleccion interna reutilizable sobre datos ambientales validados. No autoriza publicacion.
     * Solo composicion confiable; la autorizacion editorial se comprueba antes y al persistir. */
    com.huellago.backend.dtos.ResultadoGeneracionFundamentadaIADTO generarV2ConAlcanceInterno(
            ContextoRecomendacionesIAService.Resultado resultado,
            List<CatalogoAccionesAmbientalesService.Candidata> permitidas,
            java.util.Set<AccionAmbientalAdmisible.Referencia> realizadas);
    List<RecomendacionGeneradaIADTO> generar(ContextoRecomendacionesIADTO contexto);

    /** Usa el resultado interno del constructor; nunca se envían observaciones ni metadatos personales al modelo. */
    List<RecomendacionGeneradaIADTO> generarDesdeContexto(ContextoRecomendacionesIAService.Resultado resultado);

    /** Operación interna de prototipo: solo datos sintéticos, sin publicación ni persistencia de acciones BORRADOR. */
    com.huellago.backend.dtos.ResultadoGeneracionFundamentadaIADTO generarV2ParaPruebasSinteticas(
            ContextoRecomendacionesIAService.Resultado resultado,
            java.util.Set<AccionAmbientalAdmisible.Referencia> realizadas);

    /** Subconjunto explícito canónico. Solo prototipo sintético; no acredita autorización editorial. */
    com.huellago.backend.dtos.ResultadoGeneracionFundamentadaIADTO generarV2ConAlcanceParaPruebasSinteticas(
            ContextoRecomendacionesIAService.Resultado resultado,
            List<CatalogoAccionesAmbientalesService.Candidata> permitidas,
            java.util.Set<AccionAmbientalAdmisible.Referencia> realizadas);
}
