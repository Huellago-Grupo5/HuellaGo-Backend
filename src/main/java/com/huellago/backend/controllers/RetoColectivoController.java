package com.huellago.backend.controllers;

import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.dtos.RetoColectivoDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.RetoColectivoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/retos/colectivos")
public class RetoColectivoController {
    @Autowired RetoColectivoService servicio;

    @GetMapping
    public List<RetoColectivoDTO> disponibles(@AuthenticationPrincipal UserSecurity autenticado) {
        return servicio.listarDisponibles(autenticado.getUser().getId());
    }

    @GetMapping("/{id}")
    public RetoColectivoDTO consultar(@PathVariable Integer id, @AuthenticationPrincipal UserSecurity autenticado) {
        return servicio.obtener(id, autenticado.getUser().getId());
    }

    @PostMapping("/{id}/unirse")
    public ResponseEntity<UsuarioRetoRespuestaDTO> unirse(@PathVariable Integer id,
            @AuthenticationPrincipal UserSecurity autenticado) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(servicio.unirse(id, autenticado.getUser()));
    }

    @PutMapping("/participaciones/{id}/progreso")
    public UsuarioRetoRespuestaDTO progreso(@PathVariable Integer id,
            @RequestBody ActualizarProgresoDTO dto,
            @AuthenticationPrincipal UserSecurity autenticado) {
        return servicio.actualizarProgreso(id, autenticado.getUser().getId(), dto);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("mensaje", exception.getReason() == null
                        ? "No se pudo procesar el reto colectivo" : exception.getReason()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> jsonInvalido() {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "JSON o progreso inválido"));
    }
}
