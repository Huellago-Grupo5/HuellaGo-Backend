package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ResultadoPersistenciaRecomendacionesIADTO;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.GeneracionManualIAService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Exclusivamente src/test. No expone contexto, permisos, secretos ni entidades JPA. */
@RestController
final class ControladorHttpIA123 {
    private final GeneracionManualIAService generacion;
    private final SeguridadHttpIA123 seguridad;
    private final TransactionTemplate tx;
    ControladorHttpIA123(GeneracionManualIAService generacion,SeguridadHttpIA123 seguridad,TransactionTemplate tx) {
        this.generacion=generacion; this.seguridad=seguridad; this.tx=tx;
    }
    record Respuesta(String estado,List<ResultadoPersistenciaRecomendacionesIADTO.Guardada> recomendaciones,
                     String estadoEditorial,String revisionHumana,boolean publicable) { }
    @PostMapping(EntornoHttpIA123.RUTA)
    Respuesta generar(Authentication autenticacion,HttpServletRequest req) throws java.io.IOException {
        sinArgumentos(req);
        var r=generacion.generarParaPruebaInterna(autenticacion);
        var guardadas=r.persistencia()==null?List.<ResultadoPersistenciaRecomendacionesIADTO.Guardada>of():r.persistencia().resultados();
        String estado=r.estado().name();
        if(!guardadas.isEmpty()) estado=guardadas.stream().allMatch(ResultadoPersistenciaRecomendacionesIADTO.Guardada::reutilizada)
                ? "RESULTADO_REUTILIZADO" : "PROPUESTA_GUARDADA_PRUEBA_INTERNA";
        return new Respuesta(estado,guardadas,"BORRADOR","PENDIENTE_REVISION",false);
    }
    @PostMapping(EntornoHttpIA123.TOKEN)
    Map<String,Object> token(Authentication autenticacion,HttpServletRequest req) throws java.io.IOException {
        sinArgumentos(req);
        return Map.of("access_token",seguridad.emitir((UserSecurity)autenticacion.getPrincipal(),tx),
                "token_type","Bearer","expires_in",900,"alcance","PRUEBA_INTERNA");
    }
    private static void sinArgumentos(HttpServletRequest req) throws java.io.IOException {
        if((req.getQueryString()!=null && !req.getQueryString().isEmpty()) || req.getInputStream().read()!=-1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"NO_SE_ADMITEN_PARAMETROS_NI_BODY");
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,Object>> conflicto(ResponseStatusException e) {
        // Las razones de los servicios son codigos internos; no devolver texto tecnico arbitrario.
        String motivo=e.getReason();
        if(motivo==null || !motivo.matches("[A-Z_]{1,80}")) motivo="SOLICITUD_RECHAZADA";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("estado",motivo,"publicable",false));
    }
    @ExceptionHandler(OllamaClientException.class)
    ResponseEntity<Map<String,Object>> ollama(OllamaClientException e) {
        int status=switch(e.getTipo()) { case TIEMPO_AGOTADO -> 504; case NO_DISPONIBLE -> 503; case CONTEXTO_INVALIDO -> 400; default -> 502; };
        return ResponseEntity.status(status).body(Map.of("estado","OLLAMA_"+e.getTipo().name(),"publicable",false));
    }
    @ExceptionHandler({DataAccessException.class,TransactionException.class})
    ResponseEntity<Map<String,Object>> persistencia(RuntimeException e) {
        return ResponseEntity.status(503).body(Map.of("estado","PERSISTENCIA_NO_DISPONIBLE","publicable",false));
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String,Object>> interno(IllegalStateException e) {
        return ResponseEntity.status(500).body(Map.of("estado","FLUJO_INTERNO_RECHAZADO","publicable",false));
    }
}
