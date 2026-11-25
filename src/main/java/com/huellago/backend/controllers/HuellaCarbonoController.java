package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.HuellaCarbonoRespuestaDTO;
import com.huellago.backend.dtos.HuellaDesgloseDTO;
import com.huellago.backend.dtos.HuellaEquivalenciasDTO;
import com.huellago.backend.dtos.ComparacionPromedioDTO;
import com.huellago.backend.services.HuellaCarbonoService;
import com.huellago.backend.services.ReporteAmbientalService;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.security.UsuarioAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.nio.charset.StandardCharsets;

@Tag(name = "Huella de carbono", description = "Huella orientativa en kg CO2e; cálculo semanal de alcance mixto personal y hogar, no medición certificada.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/huella-carbono")
public class HuellaCarbonoController {

    @Autowired
    HuellaCarbonoService huellaCarbonoService;

    @Autowired
    UsuarioAuthorizationService usuarioAuthorizationService;

    @Autowired
    ReporteAmbientalService reporteAmbientalService;

    @Operation(summary = "Consultar historial de huellas", description = "Consultar historial de huellas. Huella orientativa en kg CO2e; cálculo semanal de alcance mixto personal y hogar, no medición certificada.")
    @GetMapping("/historial")
    public ResponseEntity<List<HuellaCarbonoRespuestaDTO>> obtenerHistorial(
            @io.swagger.v3.oas.annotations.Parameter(description = "Fecha opcional AAAA-MM-DD", example = "2026-11-25") @org.springframework.web.bind.annotation.RequestParam(name = "fecha", required = false) String fecha,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        java.time.LocalDate seleccionada = fechaConsulta(fecha);
        Long usuarioId = userSecurity.getUser().getId();
        return ResponseEntity.ok(seleccionada == null ? huellaCarbonoService.obtenerHistorial(usuarioId)
                : huellaCarbonoService.obtenerHistorial(usuarioId, seleccionada));
    }

    @Operation(summary = "Consultar equivalencias ecológicas orientativas", description = "Consultar equivalencias ecológicas orientativas. Huella orientativa en kg CO2e; cálculo semanal de alcance mixto personal y hogar, no medición certificada.")
    @GetMapping("/equivalencias")
    public ResponseEntity<HuellaEquivalenciasDTO> obtenerEquivalencias(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(
                huellaCarbonoService.obtenerEquivalencias(userSecurity.getUser().getId()));
    }

    @Operation(summary = "Consultar promedio diario estimado", description = "Consultar promedio diario estimado. Huella orientativa en kg CO2e; cálculo semanal de alcance mixto personal y hogar, no medición certificada.")
    @GetMapping("/diario")
    public ResponseEntity<HuellaCarbonoRespuestaDTO> obtenerHuellaDiaria(
            @io.swagger.v3.oas.annotations.Parameter(description = "Fecha opcional AAAA-MM-DD; devuelve promedio diario estimado de la huella semanal", example = "2026-11-25") @org.springframework.web.bind.annotation.RequestParam(name = "fecha", required = false) String fecha,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        java.time.LocalDate seleccionada = fechaConsulta(fecha);
        Long usuarioId = userSecurity.getUser().getId();
        return ResponseEntity.ok(seleccionada == null ? huellaCarbonoService.obtenerHuellaDiaria(usuarioId)
                : huellaCarbonoService.obtenerHuellaDiaria(usuarioId, seleccionada));
    }

    private java.time.LocalDate fechaConsulta(String fecha) {
        if (fecha == null) return null;
        try {
            if (!fecha.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new java.time.DateTimeException("Formato inválido");
            return java.time.LocalDate.parse(fecha, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (java.time.DateTimeException error) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "La fecha debe ser válida y tener formato AAAA-MM-DD.");
        }
    }

    @Operation(summary = "Comparar con promedio orientativo", description = "Comparar con promedio orientativo. Huella orientativa en kg CO2e; cálculo semanal de alcance mixto personal y hogar, no medición certificada.")
    @GetMapping("/comparacion-promedio")
    public ResponseEntity<ComparacionPromedioDTO> compararConPromedio(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(huellaCarbonoService.compararConPromedio(
                userSecurity.getUser().getId()));
    }

    @Operation(summary = "Descargar reporte ambiental PDF", description = "Descargar reporte ambiental PDF. Huella orientativa en kg CO2e; cálculo semanal de alcance mixto personal y hogar, no medición certificada.")
    @ApiResponse(responseCode = "200", description = "Archivo PDF adjunto", headers = @io.swagger.v3.oas.annotations.headers.Header(name = "Content-Disposition", description = "Nombre del archivo adjunto"), content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/pdf", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary")))
    @GetMapping(value = "/reporte", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> descargarReporte(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        Long usuarioId = userSecurity.getUser().getId();
        byte[] reporte = reporteAmbientalService.generarReporte(userSecurity.getUser());
        String nombreArchivo = "reporte-ambiental-huellago-" + usuarioId + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(nombreArchivo, StandardCharsets.UTF_8)
                        .build().toString())
                .contentLength(reporte.length)
                .body(reporte);
    }

    @Operation(summary = "Recalcular huella semanal", description = "Recalcular huella semanal. Huella orientativa en kg CO2e; cálculo semanal de alcance mixto personal y hogar, no medición certificada.")
    @PostMapping("/recalcular")
    public ResponseEntity<HuellaCarbonoRespuestaDTO> recalcular(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(
                huellaCarbonoService.recalcularHuella(userSecurity.getUser().getId()));
    }

    @Operation(summary = "Calcular huella inicial", description = "Calcular huella inicial. Huella orientativa en kg CO2e; cálculo semanal de alcance mixto personal y hogar, no medición certificada.")
    @PostMapping("/calcular")
    public ResponseEntity<HuellaCarbonoRespuestaDTO> calcular(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(huellaCarbonoService.calcularHuellaInicial(
                userSecurity.getUser().getId()));
    }

    @Operation(summary = "Consultar huella del propietario", description = "Consultar huella del propietario. Huella orientativa en kg CO2e; cálculo semanal de alcance mixto personal y hogar, no medición certificada.")
    @GetMapping("/{usuarioId}")
    public ResponseEntity<HuellaCarbonoRespuestaDTO> buscarPorUsuario(
            @io.swagger.v3.oas.annotations.Parameter(description = "ID del propietario autenticado") @PathVariable("usuarioId") Long usuarioId,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        usuarioAuthorizationService.validarPropietario(usuarioId, userSecurity);
        return ResponseEntity.ok(huellaCarbonoService.buscarPorUsuario(usuarioId));
    }

    @Operation(summary = "Consultar desglose por categoría", description = "Consultar desglose por categoría. Huella orientativa en kg CO2e; cálculo semanal de alcance mixto personal y hogar, no medición certificada.")
    @GetMapping("/desglose/{usuarioId}")
    public ResponseEntity<HuellaDesgloseDTO> obtenerDesglose(
            @io.swagger.v3.oas.annotations.Parameter(description = "ID del propietario autenticado") @PathVariable("usuarioId") Long usuarioId,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        usuarioAuthorizationService.validarPropietario(usuarioId, userSecurity);
        return ResponseEntity.ok(huellaCarbonoService.obtenerDesglose(usuarioId));
    }
}
