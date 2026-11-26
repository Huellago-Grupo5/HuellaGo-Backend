package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.huellago.backend.dtos.*;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.ObjetivoAmbientalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@Tag(name = "Objetivos ambientales", description = "Catálogo y selección de objetivos ambientales para el usuario autenticado.")
@SecurityRequirement(name = "bearerAuth")
@RestController @RequestMapping
public class ObjetivoAmbientalController {
    private final ObjetivoAmbientalService service;
    public ObjetivoAmbientalController(ObjetivoAmbientalService service) { this.service = service; }
    @Operation(summary = "Listar objetivos disponibles", description = "Listar objetivos disponibles. Catálogo y selección de objetivos ambientales para el usuario autenticado.")
    @GetMapping("/objetivos-ambientales")
    public ResponseEntity<List<ObjetivoAmbientalDTO>> disponibles() { return ResponseEntity.ok(service.listarDisponibles()); }
    @Operation(summary = "Consultar mis objetivos seleccionados", description = "Consultar mis objetivos seleccionados. Catálogo y selección de objetivos ambientales para el usuario autenticado.")
    @GetMapping("/usuarios/me/objetivos")
    public ResponseEntity<List<ObjetivoAmbientalDTO>> seleccionados(@AuthenticationPrincipal UserSecurity auth) { return ResponseEntity.ok(service.listarSeleccionados(auth.getUsername())); }
    @Operation(summary = "Actualizar mis objetivos seleccionados", description = "Actualizar mis objetivos seleccionados. Catálogo y selección de objetivos ambientales para el usuario autenticado.")
    @PutMapping("/usuarios/me/objetivos")
    public ResponseEntity<List<ObjetivoAmbientalDTO>> actualizar(@Valid @RequestBody SeleccionObjetivosAmbientalesDTO dto, @AuthenticationPrincipal UserSecurity auth) { return ResponseEntity.ok(service.actualizarSeleccion(auth.getUsername(), dto)); }
}
