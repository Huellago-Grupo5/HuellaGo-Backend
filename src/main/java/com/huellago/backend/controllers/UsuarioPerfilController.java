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
@CrossOrigin(origins = "*", exposedHeaders = "X-Reauthentication-Required")
@RequestMapping("/usuarios")
public class UsuarioPerfilController {

    @Autowired
    UsuarioService usuarioService;

    @PutMapping("/perfil")
    public ResponseEntity<UsuarioRespuestaDTO> actualizarPerfil(
            @jakarta.validation.Valid @RequestBody UsuarioPerfilDTO usuarioPerfilDTO,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        String anterior = userSecurity.getUsername();
        UsuarioRespuestaDTO resultado = usuarioService.actualizarPerfil(anterior, usuarioPerfilDTO);
        return ResponseEntity.ok().header("X-Reauthentication-Required",
                Boolean.toString(!anterior.equalsIgnoreCase(resultado.getCorreo()))).body(resultado);
    }
}
