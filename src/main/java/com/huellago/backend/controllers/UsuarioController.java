package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.UsuarioRegistroDTO;
import com.huellago.backend.dtos.UsuarioRespuestaDTO;
import com.huellago.backend.dtos.LoginDTO;
import com.huellago.backend.dtos.TokenDTO;
import com.huellago.backend.dtos.RecuperacionRespuestaDTO;
import com.huellago.backend.dtos.RecuperarContrasenaDTO;
import com.huellago.backend.dtos.RestablecerContrasenaDTO;
import com.huellago.backend.dtos.UsuarioPerfilDTO;
import com.huellago.backend.services.UsuarioService;
import com.huellago.backend.services.AccesoCuentaService;
import com.huellago.backend.security.UserSecurity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;

@Tag(name = "Autenticación", description = "Registro, inicio de sesión y recuperación de contraseña. El perfil exige JWT.")
@RestController
@RequestMapping("/auth")
public class UsuarioController {

    @Autowired
    UsuarioService usuarioService;

    @Autowired
    AccesoCuentaService accesoCuentaService;

    @Operation(summary = "Registrar usuario", description = "Registrar usuario. Registro, inicio de sesión y recuperación de contraseña. El perfil exige JWT.")
    @PostMapping("/register")
    public ResponseEntity<UsuarioRespuestaDTO> registrar(@Valid @RequestBody UsuarioRegistroDTO usuarioRegistroDTO) {
        return new ResponseEntity<>(usuarioService.registrar(usuarioRegistroDTO), HttpStatus.CREATED);
    }

    @Operation(summary = "Iniciar sesión y obtener JWT", description = "Iniciar sesión y obtener JWT. Registro, inicio de sesión y recuperación de contraseña. El perfil exige JWT.")
    @PostMapping("/login")
    public ResponseEntity<TokenDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return ResponseEntity.ok(accesoCuentaService.login(loginDTO));
    }

    @Operation(summary = "Solicitar recuperación de contraseña", description = "Devuelve una respuesta informativa equivalente para cuentas existentes e inexistentes. Si hay cuenta y SMTP configurado, envía un enlace de un solo uso y 15 minutos; no expone el token en JSON.")
    @PostMapping("/recuperar-contrasena")
    public ResponseEntity<RecuperacionRespuestaDTO> recuperarContrasena(
            @Valid @RequestBody RecuperarContrasenaDTO recuperarContrasenaDTO) {
        return new ResponseEntity<>(
                usuarioService.solicitarRecuperacion(recuperarContrasenaDTO), HttpStatus.OK);
    }

    @Operation(summary = "Restablecer contraseña con token", description = "Restablecer contraseña con token. Registro, inicio de sesión y recuperación de contraseña. El perfil exige JWT.")
    @PostMapping("/restablecer-contrasena")
    public ResponseEntity<Void> restablecerContrasena(
            @Valid @RequestBody RestablecerContrasenaDTO restablecerContrasenaDTO) {
        usuarioService.restablecerContrasena(restablecerContrasenaDTO);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Actualizar perfil autenticado", description = "Actualizar perfil autenticado. Registro, inicio de sesión y recuperación de contraseña. El perfil exige JWT.")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/perfil")
    public ResponseEntity<UsuarioRespuestaDTO> actualizarPerfil(
            @Valid @RequestBody UsuarioPerfilDTO usuarioPerfilDTO,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        String correoAnterior = userSecurity.getUsername();
        UsuarioRespuestaDTO usuarioActualizado = usuarioService.actualizarPerfil(
                correoAnterior, usuarioPerfilDTO);
        boolean requiereReautenticacion = !correoAnterior.equalsIgnoreCase(
                usuarioActualizado.getCorreo());

        return ResponseEntity.ok()
                .header("X-Reauthentication-Required",
                        Boolean.toString(requiereReautenticacion))
                .body(usuarioActualizado);
    }

}
