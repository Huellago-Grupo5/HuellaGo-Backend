package com.huellago.backend.controllers;

import com.huellago.backend.dtos.LogrosRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.LogrosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
@RequestMapping("/usuarios/me")
public class LogrosController {

    @Autowired
    LogrosService logrosService;

    @GetMapping("/logros")
    public ResponseEntity<LogrosRespuestaDTO> obtenerLogros(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(logrosService.obtenerLogros(userSecurity.getUser()));
    }
}
