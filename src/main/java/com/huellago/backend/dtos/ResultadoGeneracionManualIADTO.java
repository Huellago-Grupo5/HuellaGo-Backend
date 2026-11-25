package com.huellago.backend.dtos;

import com.huellago.backend.services.OrquestadorRecomendacionesIAService;
import com.huellago.backend.services.PoliticaAutorizacionEditorialIAService;
import com.huellago.backend.services.ContextoRecomendacionesIAService;
import com.huellago.backend.services.AccionAmbientalAdmisible;
import java.util.*;

/** Trazabilidad interna; no es un DTO publico ni una autorizacion editorial. */
public record ResultadoGeneracionManualIADTO(Estado estado,
        PoliticaAutorizacionEditorialIAService.Resultado elegibilidad,
        OrquestadorRecomendacionesIAService.Resultado seleccion,
        ResultadoPersistenciaRecomendacionesIADTO persistencia,
        Diagnostico diagnostico) {
    /** Compatibilidad con composiciones anteriores, sin inventar diagnosticos no disponibles. */
    public ResultadoGeneracionManualIADTO(Estado estado,
            PoliticaAutorizacionEditorialIAService.Resultado elegibilidad,
            OrquestadorRecomendacionesIAService.Resultado seleccion,
            ResultadoPersistenciaRecomendacionesIADTO persistencia) {
        this(estado, elegibilidad, seleccion, persistencia, new Diagnostico(null, Set.of(), List.of()));
    }
    public ResultadoGeneracionManualIADTO {
        Objects.requireNonNull(diagnostico);
        // Solo ordena la lista devuelta: no altera el JSON original, filas ni instantaneas guardadas.
        if (persistencia != null && persistencia.resultados().size() > 1 && elegibilidad != null) {
            var categorias = new HashMap<AccionAmbientalAdmisible.Referencia, String>();
            elegibilidad.elegibles().forEach(c -> categorias.put(
                    new AccionAmbientalAdmisible.Referencia(c.definicion().id(), c.definicion().version()), c.definicion().categoria()));
            var ordenadas = persistencia.resultados().stream().sorted(Comparator
                    .comparingInt((ResultadoPersistenciaRecomendacionesIADTO.Guardada g) -> ordenCategoria(categorias.get(
                            new AccionAmbientalAdmisible.Referencia(g.accionId(), g.versionAccion()))))
                    .thenComparing(ResultadoPersistenciaRecomendacionesIADTO.Guardada::accionId)
                    .thenComparing(ResultadoPersistenciaRecomendacionesIADTO.Guardada::versionAccion)
                    .thenComparing(ResultadoPersistenciaRecomendacionesIADTO.Guardada::enfoque, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(ResultadoPersistenciaRecomendacionesIADTO.Guardada::versionPresentacion)
                    .thenComparing(ResultadoPersistenciaRecomendacionesIADTO.Guardada::asociacionId)).toList();
            if (!ordenadas.equals(persistencia.resultados())) persistencia = new ResultadoPersistenciaRecomendacionesIADTO(ordenadas);
        }
    }

    public enum Causa { INFORMACION_AMBIENTAL_INSUFICIENTE, CONTEXTO_PARCIAL, SIN_ACCIONES_COMPATIBLES,
        ACCIONES_YA_REALIZADAS, SIN_AUTORIZACION_EDITORIAL, CATEGORIAS_FUERA_DE_ALCANCE,
        ABSTENCION_REAL_MODELO, SELECCION_RECHAZADA, SIN_PRESENTACION }
    public record Diagnostico(ContextoRecomendacionesIAService.Resultado contexto,
            Set<Causa> causas, List<String> categoriasExcluidas) {
        public Diagnostico { causas = Set.copyOf(causas); categoriasExcluidas = List.copyOf(categoriasExcluidas); }
        public Diagnostico con(Causa causa) {
            var nuevas = new HashSet<>(causas); nuevas.add(causa);
            return new Diagnostico(contexto, nuevas, categoriasExcluidas);
        }
    }

    /** Orden tecnico: Transporte, Energia, Alimentacion, Residuos; no mide impacto esperado. */
    private static int ordenCategoria(String categoria) {
        if (categoria == null) return 4;
        return switch (categoria) { case "Transporte" -> 0; case "Energía" -> 1;
            case "Alimentación" -> 2; case "Residuos" -> 3; default -> 4; };
    }
    /** Vista ordenada; la seleccion original permanece intacta para revalidacion y trazabilidad. */
    public List<ResultadoPresentacionRecomendacionesIADTO.Detalle> propuestasAceptadasOrdenadas() {
        if (seleccion == null || seleccion.presentacion() == null) return List.of();
        return seleccion.presentacion().resultados().stream()
                .filter(d -> d.estado() == ResultadoPresentacionRecomendacionesIADTO.EstadoDetalle.VISTA_PREVIA_BORRADOR)
                .sorted(Comparator.comparingInt((ResultadoPresentacionRecomendacionesIADTO.Detalle d) -> ordenCategoria(d.seleccionOriginal().categoria()))
                        .thenComparing(d -> d.presentacion().accionId()).thenComparing(d -> d.presentacion().versionAccion())
                        .thenComparing(d -> d.presentacion().enfoque(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(d -> d.presentacion().versionPresentacion())).toList();
    }
    public enum Estado { SIN_PROPUESTA, SELECCION_RECHAZADA, ABSTENCION_MODELO,
        SIN_PRESENTACION, PERSISTIDA_PRUEBA_INTERNA, PERSISTIDA_USO_UNIVERSITARIO_LOCAL }
    public boolean publicable() { return false; }
}
