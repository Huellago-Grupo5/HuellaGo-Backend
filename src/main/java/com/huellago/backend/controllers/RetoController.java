package com.huellago.backend.controllers;

import com.huellago.backend.dtos.RetoRespuestaDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.RetoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/retos")
public class RetoController {

    @Autowired
    RetoService retoService;

    @GetMapping
    public ResponseEntity<List<RetoRespuestaDTO>> listarRetosActivos() {
        return ResponseEntity.ok(retoService.listarRetosActivos());
    }

    @PostMapping("/{id}/aceptar")
    public ResponseEntity<UsuarioRetoRespuestaDTO> aceptarReto(
            @PathVariable("id") Integer retoId,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return new ResponseEntity<>(
                retoService.aceptarReto(retoId, userSecurity.getUser()),
                org.springframework.http.HttpStatus.CREATED
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> manejarResponseStatusException(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).build();
    }
}
