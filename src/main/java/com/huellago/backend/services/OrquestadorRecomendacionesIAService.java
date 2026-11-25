package com.huellago.backend.services;

import com.huellago.backend.dtos.ResultadoGeneracionFundamentadaIADTO;
import com.huellago.backend.dtos.ResultadoPresentacionRecomendacionesIADTO;
import java.util.List;
import java.util.Set;

/** Coordinacion interna experimental; no es un puerto de generacion publica.
 * La operacion sintetica anterior conserva su contrato separado. */
public interface OrquestadorRecomendacionesIAService {
    /** Coordinacion interna reutilizable, sin persistencia ni habilitacion publica. */
    Resultado orquestarInterno(ContextoRecomendacionesIAService.Resultado contexto,
            List<CatalogoAccionesAmbientalesService.Candidata> candidatas,
            Set<AccionAmbientalAdmisible.Referencia> realizadas);
    Resultado orquestarParaPruebasSinteticas(ContextoRecomendacionesIAService.Resultado contexto,
            List<CatalogoAccionesAmbientalesService.Candidata> candidatas,
            Set<AccionAmbientalAdmisible.Referencia> realizadas);

    /** Conserva la selección original. No certifica que un cliente inyectado haya llamado a un modelo real. */
    record Resultado(ResultadoGeneracionFundamentadaIADTO seleccion,
                     ResultadoPresentacionRecomendacionesIADTO presentacion,
                     com.huellago.backend.dtos.AlcanceSeleccionIADTO alcance,
                     Set<String> categoriasRespondidas) {
        public Resultado { categoriasRespondidas = Set.copyOf(categoriasRespondidas); }
        public Resultado(ResultadoGeneracionFundamentadaIADTO seleccion,
                         ResultadoPresentacionRecomendacionesIADTO presentacion) {
            this(seleccion, presentacion, null, Set.of());
        }
        public boolean publicable() { return false; }
    }
}
