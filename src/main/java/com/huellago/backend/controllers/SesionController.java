package com.huellago.backend.controllers;

import com.huellago.backend.services.SesionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
@RequestMapping("/sesiones")
public class SesionController {

    @Autowired
    SesionService sesionService;

    @DeleteMapping("/actual")
    public ResponseEntity<Void> cerrarSesion(
            @RequestHeader("Authorization") String authorizationHeader) {
        sesionService.cerrarSesion(authorizationHeader.substring(7));
        return ResponseEntity.noContent().build();
    }
}
