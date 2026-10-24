package com.huellago.backend.controllers;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.RecomendacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
}
