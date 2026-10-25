package com.huellago.backend.controllers;

import com.huellago.backend.dtos.ReporteInstitucionalDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.ReporteInstitucionalService;
import com.huellago.backend.serviceimpl.ReporteInstitucionalCsv;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*", exposedHeaders = HttpHeaders.CONTENT_DISPOSITION)
@RequestMapping("/instituciones/{institucionId}/reportes/desempeno-ambiental")
public class ReporteInstitucionalController {
    private final ReporteInstitucionalService servicio;
    private final ReporteInstitucionalCsv csv;

    public ReporteInstitucionalController(ReporteInstitucionalService servicio, ReporteInstitucionalCsv csv) {
        this.servicio = servicio;
        this.csv = csv;
    }

    @GetMapping
    public ResponseEntity<ReporteInstitucionalDTO> consultar(@PathVariable Long institucionId,
            @AuthenticationPrincipal UserSecurity usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer campanaId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.consultar(institucionId, usuario == null ? null : usuario.getUser(), desde, hasta, campanaId));
    }

    @GetMapping("/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long institucionId,
            @AuthenticationPrincipal UserSecurity usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer campanaId) {
        var reporte = servicio.consultar(institucionId, usuario == null ? null : usuario.getUser(), desde, hasta, campanaId);
        byte[] contenido;
        try { contenido = csv.generar(reporte); }
        catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el archivo del reporte");
        }
        String nombre = "reporte-institucional-" + reporte.institucion().id() + "-"
                + reporte.generadoEl().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .cacheControl(CacheControl.noStore()).contentLength(contenido.length).body(contenido);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> estado(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(Map.of("mensaje",
                error.getReason() == null ? "No se pudo consultar el reporte" : error.getReason()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> formato() {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("mensaje", "Identificador o fecha inválidos; usa fechas yyyy-MM-dd"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> fallo() {
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("mensaje", "No se pudo obtener el reporte. Intenta nuevamente más tarde"));
    }
}
