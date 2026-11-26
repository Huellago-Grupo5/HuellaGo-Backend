package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.*;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.PublicacionComunitariaService;
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
import java.util.List;
import java.util.Map;

@Tag(name = "Comunidad", description = "Permite compartir explícitamente logros propios y consultar publicaciones conforme a la privacidad.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/comunidad/publicaciones")
public class PublicacionComunitariaController {
    private final PublicacionComunitariaService servicio;

    public PublicacionComunitariaController(PublicacionComunitariaService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Compartir logro en comunidad", description = "Compartir logro en comunidad. Permite compartir explícitamente logros propios y consultar publicaciones conforme a la privacidad.")
    @PostMapping
    public ResponseEntity<PublicacionCreadaDTO> compartir(@Valid @RequestBody CompartirLogroDTO solicitud,
            @AuthenticationPrincipal UserSecurity usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.compartir(usuario.getUser(), solicitud));
    }

    @Operation(summary = "Listar publicaciones comunitarias", description = "Listar publicaciones comunitarias. Permite compartir explícitamente logros propios y consultar publicaciones conforme a la privacidad.")
    @GetMapping
    public ResponseEntity<List<PublicacionComunitariaDTO>> listar(
            @AuthenticationPrincipal UserSecurity usuario,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.listar(usuario.getUser(), pagina, tamano));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> estado(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("mensaje",
                error.getReason() == null ? "No se pudo publicar el logro" : error.getReason()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> invalido() {
        return ResponseEntity.badRequest().body(Map.of("mensaje",
                "Solicitud inválida: usa tipo INSIGNIA o RETO, origenId entero positivo y paginación válida"));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<Map<String, String>> conflicto() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje",
                "La publicación ya existe o hubo un conflicto concurrente; consulta la comunidad antes de reintentar"));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<Map<String, String>> persistencia() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("mensaje",
                "No se pudo procesar la publicación en este momento. Intenta nuevamente más tarde"));
    }
}
