package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.RankingComunitarioDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.UsuarioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rankings", description = "Ordena usuarios visibles por eco puntos y oculta los perfiles privados.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/comunidad/ranking")
public class RankingComunitarioController {
    private final UsuarioService usuarios;

    public RankingComunitarioController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @Operation(summary = "Consultar ranking comunitario", description = "Consultar ranking comunitario. Ordena usuarios visibles por eco puntos y oculta los perfiles privados.")
    @GetMapping
    public RankingComunitarioDTO obtener(@AuthenticationPrincipal UserSecurity autenticado) {
        return usuarios.obtenerRankingComunitario(autenticado.getUser());
    }
}
