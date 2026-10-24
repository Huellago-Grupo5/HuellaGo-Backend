package com.huellago.backend.controllers;

import com.huellago.backend.dtos.InstitucionRegistroDTO;
import com.huellago.backend.dtos.InstitucionRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.InstitucionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/instituciones")
public class InstitucionController {
    private final InstitucionService servicio;

    public InstitucionController(InstitucionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<InstitucionRespuestaDTO> registrar(
            @Valid @RequestBody InstitucionRegistroDTO datos,
            @AuthenticationPrincipal UserSecurity usuario) {
        InstitucionRespuestaDTO resultado = servicio.registrar(usuario.getUser().getId(), datos);
        return ResponseEntity.created(URI.create("/instituciones/" + resultado.id() + "/administracion"))
                .body(resultado);
    }

    @GetMapping("/{id}/administracion")
    public ResponseEntity<InstitucionRespuestaDTO> consultarAdministracion(
            @PathVariable Long id, @AuthenticationPrincipal UserSecurity usuario) {
        return ResponseEntity.ok(servicio.consultarAdministracion(usuario.getUser().getId(), id));
    }
}
