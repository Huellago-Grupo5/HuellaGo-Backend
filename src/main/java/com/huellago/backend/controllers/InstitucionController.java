package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.InstitucionRegistroDTO;
import com.huellago.backend.dtos.InstitucionRespuestaDTO;
import com.huellago.backend.dtos.RankingInstitucionalDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.InstitucionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@Tag(name = "Instituciones", description = "Gestión institucional con permisos de administrador y pertenencia para las consultas protegidas.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/instituciones")
public class InstitucionController {
    private final InstitucionService servicio;

    public InstitucionController(InstitucionService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Registrar institución", description = "Registrar institución. Gestión institucional con permisos de administrador y pertenencia para las consultas protegidas.")
    @PostMapping
    public ResponseEntity<InstitucionRespuestaDTO> registrar(
            @Valid @RequestBody InstitucionRegistroDTO datos,
            @AuthenticationPrincipal UserSecurity usuario) {
        InstitucionRespuestaDTO resultado = servicio.registrar(usuario.getUser().getId(), datos);
        return ResponseEntity.created(URI.create("/instituciones/" + resultado.id() + "/administracion"))
                .body(resultado);
    }

    @Operation(summary = "Consultar administración institucional", description = "Consultar administración institucional. Gestión institucional con permisos de administrador y pertenencia para las consultas protegidas.")
    @GetMapping("/{id}/administracion")
    public ResponseEntity<InstitucionRespuestaDTO> consultarAdministracion(
            @PathVariable Long id, @AuthenticationPrincipal UserSecurity usuario) {
        return ResponseEntity.ok(servicio.consultarAdministracion(usuario.getUser().getId(), id));
    }

    @Operation(summary = "Consultar ranking individual institucional", description = "Consultar ranking individual institucional. Gestión institucional con permisos de administrador y pertenencia para las consultas protegidas.")
    @GetMapping("/{id}/ranking")
    public ResponseEntity<RankingInstitucionalDTO> ranking(
            @PathVariable Long id, @AuthenticationPrincipal UserSecurity usuario) {
        return ResponseEntity.ok(servicio.consultarRanking(usuario.getUser().getId(), id));
    }
}
