package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.EcoPuntosAccionDTO;
import com.huellago.backend.dtos.EcoPuntosRespuestaDTO;
import com.huellago.backend.dtos.NivelRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.Map;

@Tag(name = "Recompensas", description = "Gestiona puntos y nivel del usuario autenticado según las reglas vigentes.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/usuarios/me")
public class EcoPuntosController {

    @Autowired
    UsuarioService usuarioService;

    @Operation(summary = "Otorgar eco puntos", description = "Otorgar eco puntos. Gestiona puntos y nivel del usuario autenticado según las reglas vigentes.")
    @PostMapping("/eco-puntos")
    public ResponseEntity<EcoPuntosRespuestaDTO> otorgarEcoPuntos(
            @RequestBody EcoPuntosAccionDTO dto,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(usuarioService.otorgarEcoPuntos(
                userSecurity.getUser(), dto));
    }

    @Operation(summary = "Consultar mi nivel", description = "Consultar mi nivel. Gestiona puntos y nivel del usuario autenticado según las reglas vigentes.")
    @org.springframework.web.bind.annotation.GetMapping("/nivel")
    public ResponseEntity<NivelRespuestaDTO> obtenerNivel(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(usuarioService.obtenerNivel(userSecurity.getUser()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> manejarResponseStatusException(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "mensaje", exception.getReason() == null
                        ? "No se pudo otorgar eco-puntos" : exception.getReason()));
    }
}
