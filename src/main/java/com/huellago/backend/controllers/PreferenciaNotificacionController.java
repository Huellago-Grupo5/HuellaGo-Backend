package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.PreferenciaNotificacionActualizarDTO;
import com.huellago.backend.dtos.PreferenciaNotificacionDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Preferencias", description = "La preferencia controla la generación futura de notificaciones y conserva el historial.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/usuarios/me/preferencias/notificaciones")
public class PreferenciaNotificacionController {

    @Autowired
    UsuarioService usuarioService;

    @Operation(summary = "Consultar preferencia de notificaciones", description = "Consultar preferencia de notificaciones. La preferencia controla la generación futura de notificaciones y conserva el historial.")
    @GetMapping
    public ResponseEntity<PreferenciaNotificacionDTO> obtener(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(usuarioService.obtenerPreferenciaNotificaciones(
                userSecurity.getUser()));
    }

    @Operation(summary = "Actualizar preferencia de notificaciones", description = "Actualizar preferencia de notificaciones. La preferencia controla la generación futura de notificaciones y conserva el historial.")
    @PutMapping
    public ResponseEntity<PreferenciaNotificacionDTO> actualizar(
            @Valid @RequestBody PreferenciaNotificacionActualizarDTO dto,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(usuarioService.actualizarPreferenciaNotificaciones(
                userSecurity.getUser(), dto));
    }
}
