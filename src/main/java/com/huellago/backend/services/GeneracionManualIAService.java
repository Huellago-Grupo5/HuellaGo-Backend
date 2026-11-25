package com.huellago.backend.services;

import com.huellago.backend.dtos.ResultadoGeneracionManualIADTO;
import org.springframework.security.core.Authentication;

/** Aplicacion interna sin registro automatico ni endpoint. Nunca habilita publicacion.
 * Authentication procede del contexto de seguridad confiable, no del cuerpo HTTP. */
public interface GeneracionManualIAService {
    ResultadoGeneracionManualIADTO generarParaPruebaInterna(Authentication autenticacion);
    /** Uso local con usuarios autenticados habituales; no concede permiso PUBLICABLE. */
    ResultadoGeneracionManualIADTO generarParaUsoUniversitarioLocal(Authentication autenticacion);
    /** Puerto futuro, cerrado mientras no exista acreditacion editorial productiva por finalidad.
     * No reutiliza permisos de prueba ni recibe finalidad/autorizaciones desde HTTP. */
    ResultadoGeneracionManualIADTO generarParaPublicacionAutorizada(Authentication autenticacion);
}
