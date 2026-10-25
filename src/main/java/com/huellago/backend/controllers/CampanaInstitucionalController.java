package com.huellago.backend.controllers;

import com.huellago.backend.dtos.*;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.CampanaInstitucionalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.*;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin("*")
public class CampanaInstitucionalController {
    private final CampanaInstitucionalService servicio;

    public CampanaInstitucionalController(CampanaInstitucionalService servicio) { this.servicio = servicio; }

    @PostMapping("/instituciones/{institucionId}/campanas")
    public ResponseEntity<CampanaRespuestaDTO> crear(@PathVariable Long institucionId,
            @AuthenticationPrincipal UserSecurity usuario, @Valid @RequestBody CampanaCrearDTO datos) {
        CampanaRespuestaDTO respuesta = servicio.crear(institucionId, usuario.getUser(), datos);
        return ResponseEntity.created(URI.create("/instituciones/" + institucionId + "/campanas/" + respuesta.id()))
                .body(respuesta);
    }

    @GetMapping("/instituciones/{institucionId}/campanas")
    public List<CampanaRespuestaDTO> listar(@PathVariable Long institucionId,
            @AuthenticationPrincipal UserSecurity usuario, @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(institucionId, usuario.getUser(), pagina, tamano);
    }

    @GetMapping("/instituciones/{institucionId}/campanas/{campanaId}")
    public CampanaRespuestaDTO detalle(@PathVariable Long institucionId, @PathVariable Integer campanaId,
            @AuthenticationPrincipal UserSecurity usuario) {
        return servicio.detalle(institucionId, campanaId, usuario.getUser());
    }

    @GetMapping("/usuarios/me/campanas")
    public List<CampanaRespuestaDTO> propias(@AuthenticationPrincipal UserSecurity usuario,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return servicio.misCampanas(usuario.getUser(), pagina, tamano);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> estado(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("mensaje",
                error.getReason() == null ? "No se pudo procesar la campaña" : error.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException error) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Datos de campaña inválidos", "errores",
                error.getBindingResult().getFieldErrors().stream()
                        .map(e -> e.getField() + ": " + e.getDefaultMessage()).toList()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> formato() {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "JSON, identificador o fecha inválidos; usa fechas yyyy-MM-dd"));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<Map<String, String>> conflicto() {
        return ResponseEntity.status(409).body(Map.of("mensaje", "Campaña duplicada o conflicto concurrente; verifica antes de reintentar"));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<Map<String, String>> persistencia() {
        return ResponseEntity.status(500).body(Map.of("mensaje", "No se pudo procesar la campaña. Intenta nuevamente más tarde"));
    }
}
