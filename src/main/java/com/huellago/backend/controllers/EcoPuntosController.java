package com.huellago.backend.controllers;

import com.huellago.backend.dtos.EcoPuntosAccionDTO;
import com.huellago.backend.dtos.EcoPuntosRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@CrossOrigin("*")
@RequestMapping("/usuarios/me")
public class EcoPuntosController {

    @Autowired
    UsuarioService usuarioService;

    @PostMapping("/eco-puntos")
    public ResponseEntity<EcoPuntosRespuestaDTO> otorgarEcoPuntos(
            @RequestBody EcoPuntosAccionDTO dto,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(usuarioService.otorgarEcoPuntos(
                userSecurity.getUser(), dto));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> manejarResponseStatusException(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).build();
    }
}
