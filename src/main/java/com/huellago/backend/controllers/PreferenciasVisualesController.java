package com.huellago.backend.controllers;

import com.huellago.backend.dtos.PreferenciasVisualesDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@Tag(name = "Preferencias", description = "Preferencias de presentación de la cuenta autenticada.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/usuarios/me/preferencias/visualizacion")
public class PreferenciasVisualesController {
    private final UsuarioService usuarios;
    public PreferenciasVisualesController(UsuarioService usuarios) { this.usuarios = usuarios; }

    @GetMapping
    @Operation(summary = "Consultar tema y unidades", description = "Devuelve únicamente las preferencias de presentación del usuario autenticado.")
    public ResponseEntity<PreferenciasVisualesDTO> obtener(@AuthenticationPrincipal UserSecurity actor) {
        return ResponseEntity.ok(usuarios.obtenerPreferenciasVisuales(actor.getUser()));
    }

    @PutMapping
    @Operation(summary = "Actualizar tema y unidades", description = "Valida y persiste el tema y las unidades del usuario autenticado.")
    public ResponseEntity<PreferenciasVisualesDTO> actualizar(@RequestBody PreferenciasVisualesDTO dto,
            @AuthenticationPrincipal UserSecurity actor) {
        return ResponseEntity.ok(usuarios.actualizarPreferenciasVisuales(actor.getUser(), dto));
    }
}
