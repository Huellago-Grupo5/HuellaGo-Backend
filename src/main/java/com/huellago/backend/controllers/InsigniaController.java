package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.InsigniaRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.InsigniaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@Tag(name = "Insignias", description = "Consulta las insignias obtenidas por el usuario autenticado.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/usuarios/me")
public class InsigniaController {

    @Autowired
    InsigniaService insigniaService;

    @Operation(summary = "Consultar mis insignias", description = "Consultar mis insignias. Consulta las insignias obtenidas por el usuario autenticado.")
    @GetMapping("/insignias")
    public ResponseEntity<List<InsigniaRespuestaDTO>> obtenerInsignias(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(
                insigniaService.obtenerInsignias(userSecurity.getUser()));
    }
}
