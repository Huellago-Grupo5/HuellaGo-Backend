package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.LogrosRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.LogrosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recompensas", description = "Consulta logros, eco puntos e insignias del usuario autenticado.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/usuarios/me")
public class LogrosController {

    @Autowired
    LogrosService logrosService;

    @Operation(summary = "Consultar mis logros", description = "Consultar mis logros. Consulta logros, eco puntos e insignias del usuario autenticado.")
    @GetMapping("/logros")
    public ResponseEntity<LogrosRespuestaDTO> obtenerLogros(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(logrosService.obtenerLogros(userSecurity.getUser()));
    }
}
