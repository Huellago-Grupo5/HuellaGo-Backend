package com.huellago.backend.services;

import com.huellago.backend.dtos.*;
import java.util.Set;

/** Segunda fase interna, sin selección, publicación, repositorios ni transacciones. */
public interface GeneradorContenidoRecomendacionesIAService {
    ResultadoContenidoRecomendacionesIADTO generarParaPruebasSinteticas(
            ContextoRecomendacionesIADTO contexto, ResultadoGeneracionFundamentadaIADTO seleccion,
            Set<AccionAmbientalAdmisible.Referencia> realizadas);
}
