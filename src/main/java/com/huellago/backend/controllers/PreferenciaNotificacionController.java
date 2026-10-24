package com.huellago.backend.controllers;

import com.huellago.backend.dtos.PreferenciaNotificacionActualizarDTO;
import com.huellago.backend.dtos.PreferenciaNotificacionDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
@RequestMapping("/usuarios/me/preferencias/notificaciones")
public class PreferenciaNotificacionController {

    @Autowired
    UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<PreferenciaNotificacionDTO> obtener(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(usuarioService.obtenerPreferenciaNotificaciones(
                userSecurity.getUser()));
    }

    @PutMapping
    public ResponseEntity<PreferenciaNotificacionDTO> actualizar(
            @Valid @RequestBody PreferenciaNotificacionActualizarDTO dto,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(usuarioService.actualizarPreferenciaNotificaciones(
                userSecurity.getUser(), dto));
    }
}
