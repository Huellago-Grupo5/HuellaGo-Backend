package com.huellago.backend.controllers;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionException;
import org.springframework.security.core.AuthenticationException;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {UsuarioController.class, UsuarioPerfilController.class})
public class CuentaErrorHandler {
    @ExceptionHandler(com.huellago.backend.security.LimiteRecuperacionException.class)
    public ResponseEntity<?> frecuencia(com.huellago.backend.security.LimiteRecuperacionException error) {
        return ResponseEntity.status(429).header("Retry-After", Long.toString(error.getSegundosEspera()))
                .body(Map.of("mensaje", error.getReason()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validacion(MethodArgumentNotValidException error) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Datos de cuenta inválidos", "errores",
                error.getBindingResult().getFieldErrors().stream().map(e -> e.getField() + ": " + e.getDefaultMessage()).sorted().toList()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> json() { return ResponseEntity.badRequest().body(Map.of("mensaje", "JSON o tipos de datos inválidos")); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> estado(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("mensaje",
                error.getReason() == null ? "No se pudo procesar la cuenta" : error.getReason()));
    }
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> credenciales() { return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales inválidas")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> conflicto() { return ResponseEntity.status(409).body(Map.of("mensaje", "El correo ya está registrado o existe un conflicto de datos")); }
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<?> persistencia() { return ResponseEntity.status(503).body(Map.of("mensaje", "No se pudo procesar la cuenta en este momento")); }
}
