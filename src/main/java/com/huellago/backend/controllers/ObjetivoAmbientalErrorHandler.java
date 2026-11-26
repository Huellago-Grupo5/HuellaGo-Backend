package com.huellago.backend.controllers;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(assignableTypes = ObjetivoAmbientalController.class)
public class ObjetivoAmbientalErrorHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacion(MethodArgumentNotValidException error) {
        String mensaje = error.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage()).filter(java.util.Objects::nonNull)
                .findFirst().orElse("La selección de objetivos ambientales no es válida.");
        return ResponseEntity.badRequest().body(Map.of("mensaje", mensaje));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> estado(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("mensaje",
                error.getReason() == null ? "No se pudo procesar la selección de objetivos ambientales." : error.getReason()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> persistencia() {
        return ResponseEntity.status(503).body(Map.of("mensaje",
                "No se pudo procesar la selección de objetivos ambientales en este momento."));
    }
}
