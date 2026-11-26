package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.ReporteInstitucionalDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.ReporteInstitucionalService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Tag(name = "Dashboard institucional", description = "Métricas agregadas de una institución; requiere autorización institucional y protege la privacidad individual.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/instituciones/{institucionId}")
public class DashboardInstitucionalController {
    private final ReporteInstitucionalService reportes;

    public DashboardInstitucionalController(ReporteInstitucionalService reportes) {
        this.reportes = reportes;
    }

    @Operation(summary = "Consultar dashboard institucional", description = "Consultar dashboard institucional. Métricas agregadas de una institución; requiere autorización institucional y protege la privacidad individual.")
    @GetMapping("/dashboard")
    public ResponseEntity<ReporteInstitucionalDTO> dashboard(@PathVariable Long institucionId,
            @AuthenticationPrincipal UserSecurity autenticado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reportes.consultar(institucionId, autenticado.getUser(), desde, hasta, null));
    }

    @Operation(summary = "Consultar variación colectiva orientativa", description = "Consultar variación colectiva orientativa. Métricas agregadas de una institución; requiere autorización institucional y protege la privacidad individual.")
    @GetMapping("/impacto-colectivo")
    public ResponseEntity<ReporteInstitucionalDTO.SeguimientoImpacto> impacto(@PathVariable Long institucionId,
            @AuthenticationPrincipal UserSecurity autenticado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reportes.consultar(institucionId, autenticado.getUser(), desde, hasta, null)
                        .seguimientoImpacto());
    }
}
