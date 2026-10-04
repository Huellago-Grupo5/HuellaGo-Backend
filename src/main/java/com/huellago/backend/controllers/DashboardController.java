package com.huellago.backend.controllers;

import com.huellago.backend.dtos.DashboardDTO;
import com.huellago.backend.services.DashboardService;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.security.UsuarioAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@CrossOrigin("*")
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    DashboardService dashboardService;

    @Autowired
    UsuarioAuthorizationService usuarioAuthorizationService;

    @GetMapping("/{usuarioId}")
    public ResponseEntity<DashboardDTO> obtenerDashboard(@PathVariable("usuarioId") Long usuarioId,
                                                         @AuthenticationPrincipal UserSecurity userSecurity) {
        usuarioAuthorizationService.validarPropietario(usuarioId, userSecurity);
        return ResponseEntity.ok(dashboardService.obtenerDashboard(usuarioId));
    }
}
