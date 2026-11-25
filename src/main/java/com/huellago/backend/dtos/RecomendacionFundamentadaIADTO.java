package com.huellago.backend.dtos;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Contrato interno V2. No reemplaza V1 ni acredita la validez científica de texto libre. */
public record RecomendacionFundamentadaIADTO(String versionContrato, List<Propuesta> resultados) {
    public RecomendacionFundamentadaIADTO { resultados = List.copyOf(resultados); }
    public enum Decision { PROPONER, ABSTENERSE }
    public enum Ambito { HOGAR, NO_APLICA }
    public enum MotivoAbstencion { DATOS_INSUFICIENTES, SIN_ACCION_COMPATIBLE, NO_VERIFICABLE }
    public enum TipoValor { TEXTO, NUMERO, BOOLEANO }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "tipo")
    @JsonSubTypes({@JsonSubTypes.Type(value = Texto.class, name = "TEXTO"),
            @JsonSubTypes.Type(value = Numero.class, name = "NUMERO"),
            @JsonSubTypes.Type(value = Booleano.class, name = "BOOLEANO")})
    public sealed interface Valor permits Texto, Numero, Booleano { }
    public record Texto(String valor) implements Valor { }
    public record Numero(BigDecimal valor) implements Valor { }
    public record Booleano(Boolean valor) implements Valor { }

    public record Evidencia(String ruta, Valor valor, Ambito ambito) { }
    public record Accion(String id, String version, Map<String, Valor> parametros) {
        public Accion { parametros = Map.copyOf(parametros); }
    }
    public record Propuesta(String categoria, Decision decision, Accion accion, List<Evidencia> evidencias,
                           String titulo, String descripcion, String motivo,
                           List<String> informacionAdicional, MotivoAbstencion motivoAbstencion) {
        public Propuesta {
            evidencias = List.copyOf(evidencias);
            informacionAdicional = List.copyOf(informacionAdicional);
        }
    }
}
