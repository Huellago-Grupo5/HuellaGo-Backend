package com.huellago.backend.controllers;

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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;

@RestController
@CrossOrigin(origins = "*", exposedHeaders = {"X-Reauthentication-Required", "Retry-After"})
@RequestMapping("/auth")
public class UsuarioController {

    @Autowired
    UsuarioService usuarioService;

    @Autowired
    AccesoCuentaService accesoCuentaService;

    @PostMapping("/register")
    public ResponseEntity<UsuarioRespuestaDTO> registrar(@Valid @RequestBody UsuarioRegistroDTO usuarioRegistroDTO) {
        return new ResponseEntity<>(usuarioService.registrar(usuarioRegistroDTO), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return ResponseEntity.ok(accesoCuentaService.login(loginDTO));
    }

    @PostMapping("/recuperar-contrasena")
    public ResponseEntity<RecuperacionRespuestaDTO> recuperarContrasena(
            @Valid @RequestBody RecuperarContrasenaDTO recuperarContrasenaDTO) {
        return new ResponseEntity<>(
                usuarioService.solicitarRecuperacion(recuperarContrasenaDTO), HttpStatus.OK);
    }

    @PostMapping("/restablecer-contrasena")
    public ResponseEntity<Void> restablecerContrasena(
            @Valid @RequestBody RestablecerContrasenaDTO restablecerContrasenaDTO) {
        usuarioService.restablecerContrasena(restablecerContrasenaDTO);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

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
