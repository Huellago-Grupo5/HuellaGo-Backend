package com.huellago.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(assignableTypes = HabitoController.class)
public class HabitoErrorHandler {
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<?> validacion(org.springframework.web.bind.MethodArgumentNotValidException error) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Datos de hábitos inválidos", "errores",
                error.getBindingResult().getFieldErrors().stream()
                        .map(e -> e.getField() + ": " + e.getDefaultMessage()).sorted().toList()));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<?> persistencia() {
        return ResponseEntity.status(503).body(Map.of("mensaje", "No se pudo procesar el hábito en este momento"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> estado(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of(
                "mensaje", error.getReason() == null ? "No se pudo procesar el hábito" : error.getReason()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> jsonInvalido() {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "JSON o tipos de datos inválidos"));
    }
}
