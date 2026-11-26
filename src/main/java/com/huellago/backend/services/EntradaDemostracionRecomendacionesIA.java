package com.huellago.backend.services;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/** Adaptacion optativa del endpoint manual al motor IA. Su ausencia conserva LEGADO.
 * El nombre se conserva por compatibilidad; no concede permisos PUBLICABLE. */
public interface EntradaDemostracionRecomendacionesIA {
    ResponseEntity<GeneracionRecomendacionesDTO> generar(Authentication autenticacion,HttpServletRequest solicitud);
}
