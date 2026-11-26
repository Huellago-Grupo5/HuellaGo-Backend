package com.huellago.backend.services;

import com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.TipoValor;
import com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.Valor;
import java.util.Map;
import java.util.Set;

/** Definición suministrada por el llamador de confianza, nunca por la respuesta del modelo. Sin catálogo incorporado. */
public record AccionAmbientalAdmisible(String id, String version, String categoria,
                                     Map<String, Set<Valor>> condiciones,
                                     Set<String> evidenciasRequeridas,
                                     Map<String, ParametroPermitido> parametros,
                                     Set<String> informacionAdicionalPermitida) {
    public AccionAmbientalAdmisible {
        condiciones = condiciones.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
        evidenciasRequeridas = Set.copyOf(evidenciasRequeridas);
        parametros = Map.copyOf(parametros);
        informacionAdicionalPermitida = Set.copyOf(informacionAdicionalPermitida);
    }
    /** Lista finita de valores permitidos: no acepta texto o números arbitrarios del modelo. */
    public record ParametroPermitido(TipoValor tipo, Set<Valor> valoresPermitidos, boolean obligatorio) {
        public ParametroPermitido { valoresPermitidos = Set.copyOf(valoresPermitidos); }
    }
    public record Referencia(String id, String version) { }
}
