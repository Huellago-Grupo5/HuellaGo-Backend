package com.huellago.backend.serviceimpl;
import com.huellago.backend.services.*;
import org.springframework.security.core.Authentication;
import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import java.util.*;
/** Controles del lanzador anterior, delegando las reglas compartidas. */
final class ProteccionDemoIA127 {
    static final long USUARIO=ProteccionAcademicaIA.USUARIO;
    static final String FINALIDAD=ProteccionAcademicaIA.FINALIDAD, ACCION=ProteccionAcademicaIA.ACCION;
    static EntornoHttpIA123 entorno(Map<String,String> v) {
        if(!"true".equals(v.get("HUELLAGO_IA127_ACTIVAR")) || !FINALIDAD.equals(v.get("HUELLAGO_IA127_MODO")))
            throw new IllegalStateException("Demostracion IA127 desactivada");
        if(!"127.0.0.1".equals(v.get("HUELLAGO_IA127_BIND")) || !"8081".equals(v.get("HUELLAGO_IA127_PUERTO")))
            throw new IllegalArgumentException("Interfaz IA127 no autorizada");
        Map<String,String> interno=new HashMap<>(); interno.put(EntornoHttpIA123.ACTIVACION,"true");
        for(String campo:List.of("PG_URL","PG_USUARIO","PG_CLAVE","CONFIRMAR_BASE")) {
            String valor=v.get("HUELLAGO_IA127_"+campo); if(valor!=null) interno.put("HUELLAGO_IA123_"+campo,valor);
        }
        return EntornoHttpIA123.validar(interno);
    }

    static void identidad(Authentication a) { ProteccionAcademicaIA.identidad(a); }
    static void auto(ContextoRecomendacionesIADTO c) { ProteccionAcademicaIA.auto(c); }
    static CapturaContextoAmbientalIAService captura(CapturaContextoAmbientalIAService c) { return ProteccionAcademicaIA.captura(c); }
    static PoliticaAutorizacionEditorialIAService editorial(PoliticaAutorizacionEditorialIAService e) { return ProteccionAcademicaIA.editorial(e); }
}
