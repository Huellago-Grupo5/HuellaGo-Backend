package com.huellago.backend.controllers;

import com.huellago.backend.dtos.InsigniaRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.InsigniaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/usuarios/me")
public class InsigniaController {

    @Autowired
    InsigniaService insigniaService;

    @GetMapping("/insignias")
    public ResponseEntity<List<InsigniaRespuestaDTO>> obtenerInsignias(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(
                insigniaService.obtenerInsignias(userSecurity.getUser()));
    }
}
