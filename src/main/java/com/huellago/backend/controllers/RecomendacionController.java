package com.huellago.backend.controllers;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.dtos.ConversionRecomendacionDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.RecomendacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
@RequestMapping("/usuarios/me/recomendaciones")
public class RecomendacionController {

    @Autowired
    RecomendacionService recomendacionService;

    @PostMapping("/generar")
    public ResponseEntity<GeneracionRecomendacionesDTO> generar(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(recomendacionService.generar(userSecurity.getUser()));
    }

    @PostMapping("/{id}/convertir-en-reto")
    public ResponseEntity<ConversionRecomendacionDTO> convertirEnReto(
            @PathVariable Integer id, @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recomendacionService.convertirEnReto(id, userSecurity.getUser()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> manejarError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("mensaje",
                exception.getReason() == null ? "No se pudo convertir la recomendación" : exception.getReason()));
    }
}
