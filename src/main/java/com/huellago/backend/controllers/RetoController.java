package com.huellago.backend.controllers;

import com.huellago.backend.dtos.RetoRespuestaDTO;
import com.huellago.backend.services.RetoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/retos")
public class RetoController {

    @Autowired
    RetoService retoService;

    @GetMapping
    public ResponseEntity<List<RetoRespuestaDTO>> listarRetosActivos() {
        return ResponseEntity.ok(retoService.listarRetosActivos());
    }
}
