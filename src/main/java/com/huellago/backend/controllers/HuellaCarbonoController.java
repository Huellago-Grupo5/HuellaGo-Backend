package com.huellago.backend.controllers;

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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@CrossOrigin("*")
@RequestMapping("/huella-carbono")
public class HuellaCarbonoController {

    @Autowired
    HuellaCarbonoService huellaCarbonoService;

    @Autowired
    UsuarioAuthorizationService usuarioAuthorizationService;

    @Autowired
    ReporteAmbientalService reporteAmbientalService;

    @GetMapping("/historial")
    public ResponseEntity<List<HuellaCarbonoRespuestaDTO>> obtenerHistorial(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(
                huellaCarbonoService.obtenerHistorial(userSecurity.getUser().getId()));
    }

    @GetMapping("/equivalencias")
    public ResponseEntity<HuellaEquivalenciasDTO> obtenerEquivalencias(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(
                huellaCarbonoService.obtenerEquivalencias(userSecurity.getUser().getId()));
    }

    @GetMapping("/diario")
    public ResponseEntity<HuellaCarbonoRespuestaDTO> obtenerHuellaDiaria(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(
                huellaCarbonoService.obtenerHuellaDiaria(userSecurity.getUser().getId()));
    }

    @GetMapping("/comparacion-promedio")
    public ResponseEntity<ComparacionPromedioDTO> compararConPromedio(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(huellaCarbonoService.compararConPromedio(
                userSecurity.getUser().getId()));
    }

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

    @PostMapping("/recalcular")
    public ResponseEntity<HuellaCarbonoRespuestaDTO> recalcular(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(
                huellaCarbonoService.recalcularHuella(userSecurity.getUser().getId()));
    }

    @PostMapping("/calcular")
    public ResponseEntity<HuellaCarbonoRespuestaDTO> calcular(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(huellaCarbonoService.calcularHuellaInicial(
                userSecurity.getUser().getId()));
    }

    @GetMapping("/{usuarioId}")
    public ResponseEntity<HuellaCarbonoRespuestaDTO> buscarPorUsuario(
            @PathVariable("usuarioId") Long usuarioId,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        usuarioAuthorizationService.validarPropietario(usuarioId, userSecurity);
        return ResponseEntity.ok(huellaCarbonoService.buscarPorUsuario(usuarioId));
    }

    @GetMapping("/desglose/{usuarioId}")
    public ResponseEntity<HuellaDesgloseDTO> obtenerDesglose(
            @PathVariable("usuarioId") Long usuarioId,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        usuarioAuthorizationService.validarPropietario(usuarioId, userSecurity);
        return ResponseEntity.ok(huellaCarbonoService.obtenerDesglose(usuarioId));
    }
}
