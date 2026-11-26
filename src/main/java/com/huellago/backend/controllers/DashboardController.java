package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.DashboardDTO;
import com.huellago.backend.services.DashboardService;
import com.huellago.backend.security.UserSecurity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Dashboard", description = "Consulta los indicadores del usuario autenticado sin exponer datos ajenos.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    DashboardService dashboardService;

    @Operation(summary = "Consultar dashboard personal", description = "Consultar dashboard personal. Consulta los indicadores del usuario autenticado sin exponer datos ajenos.")
    @GetMapping
    public ResponseEntity<DashboardDTO> obtenerDashboard(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        Long usuarioId = userSecurity.getUser().getId();
        return ResponseEntity.ok(dashboardService.obtenerDashboard(usuarioId));
    }
}
