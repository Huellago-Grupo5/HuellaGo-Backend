package com.huellago.backend.services;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Puerto interno: el llamador deberá obtener el ID desde la identidad autenticada. */
public interface ContextoRecomendacionesIAService {
    Resultado construir(Long usuarioId);

    /** No es un contrato HTTP ni incorpora entidades o identidad personal. */
    record Resultado(ContextoRecomendacionesIADTO contexto, List<String> categoriasIncompletas,
                     List<String> observaciones, List<String> reglasInterpretacion, ResumenHuella resumenHuella) {
        public Resultado(ContextoRecomendacionesIADTO contexto, List<String> categoriasIncompletas,
                List<String> observaciones, List<String> reglasInterpretacion) {
            this(contexto, categoriasIncompletas, observaciones, reglasInterpretacion, null);
        }
        public Resultado {
            categoriasIncompletas = List.copyOf(categoriasIncompletas);
            observaciones = List.copyOf(observaciones);
            reglasInterpretacion = List.copyOf(reglasInterpretacion);
        }

        /** Presencia de categorias ya normalizadas por el constructor, no otra validacion ambiental. */
        public List<String> categoriasValidas() {
            if (contexto == null) return List.of();
            var categorias = new java.util.ArrayList<String>();
            if (contexto.transporte() != null) categorias.add("Transporte");
            if (contexto.energia() != null) categorias.add("Energía");
            if (contexto.alimentacion() != null) categorias.add("Alimentación");
            if (contexto.residuos() != null) categorias.add("Residuos");
            return List.copyOf(categorias);
        }
    }

    enum Vigencia { VIGENTE, INEXISTENTE, OBSOLETA, INVALIDA, NO_VERIFICABLE }
    enum Comparabilidad { NO_ACREDITADA, PERIMETROS_NO_COMPARABLES }
    enum Perimetro { REGISTRO_USUARIO_NO_ACREDITADO, HOGAR_SEGUN_CONTEXTO, NO_ACREDITADO }
    enum Limitacion { MODELO_ORIENTATIVO_NO_CERTIFICADO, ORIGEN_HISTORICO_NO_TRAZADO,
        METODOLOGIA_Y_ATRIBUCION_PENDIENTES, CONTEXTO_PARCIAL, SIN_HUELLA,
        IDENTIDAD_O_PROPIETARIO_INVALIDO, FECHA_INVALIDA, COMPONENTES_AUSENTES,
        VALORES_INVALIDOS, HABITOS_POSTERIORES, FECHAS_HABITOS_NO_VERIFICABLES,
        PERIMETRO_HOGAR_NO_ATRIBUIBLE_AL_USUARIO }

    /** Orden numérico descriptivo de la huella mixta, sin atribución personal ni ahorro esperado. */
    record ReferenciaNumerica(String categoria, BigDecimal kgCo2eSemanal, Perimetro perimetro) { }

    /** Datos escalares de lectura; nunca una medicion certificada o una estimacion de ahorro.
     * El modelo historico no conserva metadatos que acrediten comparabilidad. No inventar un foco. */
    record ResumenHuella(Long referencia, LocalDateTime fecha, BigDecimal totalRegistrado,
            Map<String, BigDecimal> componentes, Vigencia vigencia, Comparabilidad comparabilidad,
            Map<String, Perimetro> perimetros, Set<Limitacion> limitaciones) {
        public ResumenHuella {
            componentes = Map.copyOf(componentes); perimetros = Map.copyOf(perimetros);
            limitaciones = Set.copyOf(limitaciones);
        }
        public String unidad() { return "kg CO2e"; }
        public String periodo() { return "semanal"; }
        public String origen() { return "MODELO_ORIENTATIVO_REGISTRADO_SIN_TRAZABILIDAD_METODOLOGICA"; }
        /** Sin acreditacion metodologica no hay un conjunto comparable del que deducir maximos. */
        public List<String> focos() { return List.of(); }
        public List<ReferenciaNumerica> referenciasNumericas() {
            if (vigencia != Vigencia.VIGENTE) return List.of();
            return componentes.entrySet().stream()
                    .map(e -> new ReferenciaNumerica(e.getKey(), e.getValue(), perimetros.get(e.getKey())))
                    .sorted(java.util.Comparator.comparing(ReferenciaNumerica::kgCo2eSemanal).reversed()
                            .thenComparing(ReferenciaNumerica::categoria))
                    .toList();
        }
        @Override public String toString() { return "ResumenHuella[vigencia=" + vigencia + ", valores omitidos]"; }
    }
}
