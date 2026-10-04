package com.huellago.backend.controllers;

import com.huellago.backend.dtos.UsuarioPerfilDTO;
import com.huellago.backend.dtos.UsuarioRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
@RequestMapping("/usuarios")
public class UsuarioPerfilController {

    @Autowired
    UsuarioService usuarioService;

    @PutMapping("/perfil")
    public ResponseEntity<UsuarioRespuestaDTO> actualizarPerfil(
            @RequestBody UsuarioPerfilDTO usuarioPerfilDTO,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(
                usuarioService.actualizarPerfil(userSecurity.getUsername(), usuarioPerfilDTO));
    }
}
