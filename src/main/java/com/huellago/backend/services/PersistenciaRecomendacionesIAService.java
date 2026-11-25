package com.huellago.backend.services;

import com.huellago.backend.dtos.*;
import org.springframework.security.core.Authentication;

/** Puerto interno no conectado a HTTP. No infiere ni publica; PUBLICABLE está cerrado. */
public interface PersistenciaRecomendacionesIAService {
    enum Destino { PRUEBA_INTERNA, USO_UNIVERSITARIO_LOCAL, PUBLICABLE }
    /** Capturar antes de selección para detectar cambios de receta editorial al confirmar. */
    String referenciaEditorial(Authentication autenticacion, CapturaContextoAmbientalIADTO captura);
    /** Finalidad explicita de composicion confiable. La operacion anterior conserva PRUEBA_INTERNA.
     * PUBLICABLE se rechaza hasta disponer de acreditacion editorial productiva suficiente. */
    String referenciaEditorial(Authentication autenticacion, CapturaContextoAmbientalIADTO captura, Destino destino);
    ResultadoPersistenciaRecomendacionesIADTO persistir(Authentication autenticacion,
            CapturaContextoAmbientalIADTO captura, OrquestadorRecomendacionesIAService.Resultado seleccion,
            String referenciaEditorial, Destino destino);
}
