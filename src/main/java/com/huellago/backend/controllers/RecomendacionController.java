package com.huellago.backend.controllers;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.dtos.ConversionRecomendacionDTO;
import com.huellago.backend.dtos.RecomendacionCompletadaDTO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.RecomendacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
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

    @PostMapping("/{id}/convertir-en-reto")
    public ResponseEntity<ConversionRecomendacionDTO> convertirEnReto(
            @PathVariable Integer id, @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recomendacionService.convertirEnReto(id, userSecurity.getUser()));
    }

    @PutMapping("/{id}/completar")
    public ResponseEntity<RecomendacionCompletadaDTO> completar(
            @PathVariable Integer id, @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(recomendacionService.completar(id, userSecurity.getUser()));
    }

    @GetMapping("/historial")
    public ResponseEntity<List<RecomendacionCompletadaDTO>> historial(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(recomendacionService.historialCompletadas(userSecurity.getUser()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> manejarIdentificadorInvalido() {
        return ResponseEntity.badRequest().body(Map.of("mensaje",
                "El identificador de la recomendación debe ser un número entero válido"));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<Map<String, String>> manejarPersistencia() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("mensaje",
                "No se pudo procesar la recomendación. Intenta nuevamente más tarde"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> manejarError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("mensaje",
                exception.getReason() == null ? "No se pudo convertir la recomendación" : exception.getReason()));
    }
}
