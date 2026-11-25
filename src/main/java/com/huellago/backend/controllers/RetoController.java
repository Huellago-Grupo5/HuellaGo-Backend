package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.RetoRespuestaDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.RetoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.Map;

@Tag(name = "Retos", description = "Lista retos disponibles por clasificación y permite aceptar retos propios.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/retos")
public class RetoController {

    @Autowired
    RetoService retoService;

    @Operation(summary = "Listar retos disponibles", description = "Listar retos disponibles. Lista retos disponibles por clasificación y permite aceptar retos propios.")
    @GetMapping
    public ResponseEntity<List<RetoRespuestaDTO>> listarRetosActivos(
            @AuthenticationPrincipal UserSecurity userSecurity,
            @RequestParam(required = false) String clasificacion) {
        return ResponseEntity.ok(retoService.listarRetosDisponibles(userSecurity.getUser().getId(), clasificacion));
    }

    @Operation(summary = "Aceptar reto", description = "Aceptar reto. Lista retos disponibles por clasificación y permite aceptar retos propios.")
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
    public ResponseEntity<Map<String, String>> manejarResponseStatusException(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "mensaje", exception.getReason() == null ? "No se pudo aceptar el reto" : exception.getReason()));
    }
}
