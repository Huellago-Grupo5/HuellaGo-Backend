package com.huellago.backend.controllers;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(assignableTypes = HuellaCarbonoController.class)
public class HuellaCarbonoErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> estado(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of(
                "mensaje", error.getReason() == null ? "No se pudo procesar la huella" : error.getReason()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> persistencia() {
        return ResponseEntity.status(503).body(Map.of(
                "mensaje", "No se pudo procesar la huella en este momento"));
    }
}
