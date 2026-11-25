package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.PrivacidadActualizarDTO;
import com.huellago.backend.dtos.PrivacidadDTO;
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

@Tag(name = "Privacidad", description = "La visibilidad comunitaria determina la presencia en rankings y publicaciones.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/usuarios/me/preferencias/privacidad")
public class PrivacidadController {

    @Autowired
    UsuarioService usuarioService;

    @Operation(summary = "Consultar preferencia de privacidad", description = "Consultar preferencia de privacidad. La visibilidad comunitaria determina la presencia en rankings y publicaciones.")
    @GetMapping
    public ResponseEntity<PrivacidadDTO> obtener(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(usuarioService.obtenerPrivacidad(
                userSecurity.getUser()));
    }

    @Operation(summary = "Actualizar preferencia de privacidad", description = "Actualizar preferencia de privacidad. La visibilidad comunitaria determina la presencia en rankings y publicaciones.")
    @PutMapping
    public ResponseEntity<PrivacidadDTO> actualizar(
            @Valid @RequestBody PrivacidadActualizarDTO dto,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(usuarioService.actualizarPrivacidad(
                userSecurity.getUser(), dto));
    }
}
