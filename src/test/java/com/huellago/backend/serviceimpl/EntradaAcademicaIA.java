package com.huellago.backend.serviceimpl;
import com.huellago.backend.dtos.*;
import com.huellago.backend.repositories.UsuarioRecomendacionIARepository;
import com.huellago.backend.services.GeneracionManualIAService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** Conserva la composicion historica de pruebas; no interviene en el arranque habitual. */
class EntradaAcademicaIA extends EntradaRecomendacionesIA {
    private final GeneracionManualIAService generacion;
    EntradaAcademicaIA(GeneracionManualIAService g,UsuarioRecomendacionIARepository a,org.springframework.transaction.PlatformTransactionManager tm) {
        super(g,a,tm); generacion=g;
    }
    @Override protected Long identidad(Authentication a,HttpServletRequest req) {
        if(!"127.0.0.1".equals(req.getLocalAddr()) || req.getLocalPort()!=8081 || !"127.0.0.1".equals(req.getRemoteAddr()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"ENTORNO_HTTP_NO_AUTORIZADO");
        ProteccionAcademicaIA.identidad(a); return ProteccionAcademicaIA.USUARIO;
    }
    @Override protected ResultadoGeneracionManualIADTO ejecutar(Authentication a) { return generacion.generarParaPruebaInterna(a); }
    @Override protected boolean accionPermitida(String id,String version) { return ProteccionAcademicaIA.ACCION.equals(id) && "1".equals(version); }
    @Override protected String estadoGuardada() { return "PROPUESTA_GUARDADA_PRUEBA_INTERNA"; }
    @Override protected GeneracionRecomendacionesDTO responder(String e) { return respuesta(e); }
    static GeneracionRecomendacionesDTO respuesta(String e) { return respuestaPara(e,ProteccionAcademicaIA.FINALIDAD); }
}
