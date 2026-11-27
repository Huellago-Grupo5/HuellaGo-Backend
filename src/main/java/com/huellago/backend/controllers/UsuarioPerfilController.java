package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.UsuarioPerfilDTO;
import com.huellago.backend.dtos.UsuarioRespuestaDTO;
import com.huellago.backend.dtos.CambiarContrasenaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Usuarios y perfiles", description = "Actualiza exclusivamente el perfil de la identidad autenticada.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/usuarios")
public class UsuarioPerfilController {

    @Autowired
    UsuarioService usuarioService;

    @Operation(summary = "Consultar mi perfil", description = "Devuelve los datos personales de la identidad autenticada sin exponer credenciales.")
    @GetMapping("/perfil")
    public ResponseEntity<UsuarioRespuestaDTO> obtenerPerfil(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(usuarioService.obtenerPerfil(userSecurity.getUser()));
    }

    @Operation(summary = "Actualizar mi perfil", description = "Actualizar mi perfil. Actualiza exclusivamente el perfil de la identidad autenticada.")
    @PutMapping("/perfil")
    public ResponseEntity<UsuarioRespuestaDTO> actualizarPerfil(
            @jakarta.validation.Valid @RequestBody UsuarioPerfilDTO usuarioPerfilDTO,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        String anterior = userSecurity.getUsername();
        UsuarioRespuestaDTO resultado = usuarioService.actualizarPerfil(anterior, usuarioPerfilDTO);
        return ResponseEntity.ok().header("X-Reauthentication-Required",
                Boolean.toString(!anterior.equalsIgnoreCase(resultado.getCorreo()))).body(resultado);
    }

    @Operation(summary = "Cambiar mi contraseña", description = "Cambia la contraseña de la identidad autenticada después de validar la contraseña actual y los requisitos de seguridad.")
    @org.springframework.web.bind.annotation.PostMapping("/perfil/contrasena")
    public ResponseEntity<Void> cambiarContrasena(
            @jakarta.validation.Valid @RequestBody CambiarContrasenaDTO dto,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        usuarioService.cambiarContrasena(userSecurity.getUser(), dto.getContrasenaActual(),
                dto.getContrasenaNueva());
        return ResponseEntity.noContent().build();
    }
}
