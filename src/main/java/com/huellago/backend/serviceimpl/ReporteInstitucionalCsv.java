package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ReporteInstitucionalDTO;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Formato RFC 4180: UTF-8 con BOM para Excel; no modifica el reporte ni consulta datos. */
@Component
public class ReporteInstitucionalCsv {
    private static final List<String> CABECERAS = List.of("tipo_fila", "institucion_id", "institucion_nombre",
            "generado_el", "filtro_desde", "filtro_hasta", "filtro_campana_id", "total_campanas",
            "campanas_futuras", "campanas_activas", "campanas_finalizadas", "campanas_no_disponibles",
            "campanas_datos_incompletos", "total_participaciones_registradas", "usuarios_participantes_unicos",
            "participaciones_consideradas_en_progreso", "progreso_general_ponderado_porcentaje",
            "campanas_con_resultado", "campanas_exitosas", "campanas_no_alcanzadas", "campana_id",
            "campana_nombre", "objetivo", "fecha_inicio", "fecha_fin", "estado_campana", "estado_colectivo",
            "participaciones_registradas_campana", "participantes_seguimiento", "progreso_colectivo_porcentaje",
            "resultado_final", "fecha_cierre", "advertencias");

    public byte[] generar(ReporteInstitucionalDTO reporte) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        fila(csv, CABECERAS);
        if (reporte.campanas().isEmpty()) fila(csv, datos(reporte, null));
        else for (var campana : reporte.campanas()) fila(csv, datos(reporte, campana));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<Object> datos(ReporteInstitucionalDTO r, ReporteInstitucionalDTO.Campana c) {
        var s = r.resumen();
        List<Object> fila = new ArrayList<>(Arrays.asList(c == null ? "RESUMEN_SIN_CAMPANAS" : "CAMPANA",
                r.institucion().id(), r.institucion().nombre(), r.generadoEl(), r.filtros().desde(),
                r.filtros().hasta(), r.filtros().campanaId(), s.totalCampanas(), s.futuras(), s.activas(),
                s.finalizadas(), s.noDisponibles(), s.datosIncompletos(), s.totalParticipacionesRegistradas(),
                s.usuariosParticipantesUnicos(), s.participacionesConsideradasEnProgreso(), s.progresoGeneralPonderado(),
                s.conResultado(), s.exitosas(), s.noAlcanzadas()));
        if (c == null) fila.addAll(Collections.nCopies(12, null));
        else fila.addAll(Arrays.asList(c.id(), c.nombre(), c.objetivo(), c.fechaInicio(), c.fechaFin(), c.estado(),
                c.estadoColectivo(), c.participacionesRegistradas(), c.participantes(), c.progresoColectivo(),
                c.resultadoFinal(), c.fechaCierre()));
        fila.add(String.join(" | ", r.advertencias()));
        return fila;
    }

    private void fila(StringBuilder csv, List<?> valores) {
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) csv.append(',');
            Object valor = valores.get(i);
            String texto = valor == null ? "" : valor instanceof BigDecimal decimal
                    ? decimal.toPlainString() : valor.toString();
            // Las comillas CSV no neutralizan fórmulas. Proteger también prefijos ocultos.
            if (valor instanceof String && formula(texto)) texto = "'" + texto;
            csv.append('"').append(texto.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    private boolean formula(String texto) {
        if (texto.startsWith("\t") || texto.startsWith("\r") || texto.startsWith("\n")) return true;
        for (int i = 0; i < texto.length();) {
            int caracter = texto.codePointAt(i);
            if (!Character.isWhitespace(caracter) && !Character.isSpaceChar(caracter)
                    && !Character.isISOControl(caracter) && Character.getType(caracter) != Character.FORMAT) {
                return caracter == '=' || caracter == '+' || caracter == '-' || caracter == '@';
            }
            i += Character.charCount(caracter);
        }
        return false;
    }
}
