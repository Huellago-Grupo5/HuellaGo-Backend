package com.huellago.backend.serviceimpl;

import com.huellago.backend.services.*;
import com.huellago.backend.services.PoliticaAutorizacionEditorialIAService.*;
import java.util.*;

/** APTA_PARA_PRUEBA_INTERNA por decisión del PASOS 114/127/136. Exclusivamente composicion academica local aislada del PASO 136.
 * No habilita publicación, usuarios reales, PUBLICABLE o aprobación científica. */
final class AutorizacionAcademicaIA implements Registro {
    private static final String ACCION = "TR_PLANIFICAR_DESPLAZAMIENTOS";
    private static final Map<String,String> AUTORIZADAS = Map.of(
            "AGRUPACION", "68ac3e60bc94150cdad8734a486f71094eaca769b7fd2e000ef2280485513977",
            "NECESIDAD", "5a63fa0c2cafe7e0c728618efc6fb5ba4c7519bf66995ff84bc10e3d12b3dae8",
            "SIN_ENFOQUE", "3b650417e025042186bfa88eac1df05a0d67f417809c3bafa6438f65fe804440");
    private static final Set<String> CAMPOS = Set.of("/transporte/medio", "/transporte/kmSemana", "/transporte/diasSemana");
    private final PresentacionRecomendacionesIAService presentador;
    AutorizacionAcademicaIA() { this(new PresentacionRecomendacionesIAServiceImpl()); }
    AutorizacionAcademicaIA(PresentacionRecomendacionesIAService presentador) {
        this.presentador = Objects.requireNonNull(presentador);
    }
    @Override public List<Autorizacion> consultar() {
        var familia = presentador.definicionesParaRevision().stream().filter(d -> ACCION.equals(d.accionId())).toList();
        if (familia.size() != 3) throw discrepancia();
        Set<String> vistas = new HashSet<>(); List<Autorizacion> permisos = new ArrayList<>();
        var politica = new PoliticaAutorizacionEditorialIAServiceImpl();
        for(var d:familia) {
            String esperada = AUTORIZADAS.get(d.enfoque());
            if (!"1".equals(d.versionAccion()) || !"1".equals(d.versionPresentacion())
                    || esperada == null || !vistas.add(d.enfoque()) || !CAMPOS.equals(d.camposContextoPermitidos())
                    || !esperada.equals(politica.huellaDefinicion(d))) throw discrepancia();
            permisos.add(new Autorizacion(ACCION,"1",d.enfoque(),"1",esperada,CAMPOS));
        }
        if (!vistas.equals(AUTORIZADAS.keySet())) throw discrepancia();
        return List.copyOf(permisos);
    }
    private IllegalStateException discrepancia() {
        return new IllegalStateException("Autorización de prueba interna no coincide con las definiciones aprobadas para el PASOS 114/127/136");
    }
}
