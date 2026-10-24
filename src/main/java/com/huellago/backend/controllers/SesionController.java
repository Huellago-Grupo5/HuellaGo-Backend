package com.huellago.backend.controllers;

import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.SesionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@CrossOrigin("*")
public class SesionController {

    @Autowired
    SesionService sesionService;

    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader("Authorization") String authorizationHeader,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        try {
            sesionService.cerrarSesion(userSecurity, extraerToken(authorizationHeader));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo cerrar la sesi\u00f3n");
        }
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada correctamente"));
    }

    @DeleteMapping("/sesiones/actual")
    public ResponseEntity<Void> cerrarSesionActual(
            @RequestHeader("Authorization") String authorizationHeader,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        try {
            sesionService.cerrarSesion(userSecurity, extraerToken(authorizationHeader));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo cerrar la sesi\u00f3n");
        }
        return ResponseEntity.noContent().build();
    }

    private String extraerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")
                || authorizationHeader.length() <= 7) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "El encabezado Bearer es inválido");
        }
        return authorizationHeader.substring(7);
    }
}
