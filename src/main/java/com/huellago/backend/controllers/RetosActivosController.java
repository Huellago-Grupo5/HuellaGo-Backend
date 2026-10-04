package com.huellago.backend.controllers;

import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.RetoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/retos-activos")
public class RetosActivosController {

    @Autowired
    RetoService retoService;

    @GetMapping
    public ResponseEntity<List<UsuarioRetoRespuestaDTO>> listarRetosActivos(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(
                retoService.listarRetosActivos(userSecurity.getUser().getId()));
    }

    @PutMapping("/{id}/progreso")
    public ResponseEntity<UsuarioRetoRespuestaDTO> actualizarProgreso(
            @PathVariable("id") Integer usuarioRetoId,
            @RequestBody ActualizarProgresoDTO actualizarProgresoDTO,
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(retoService.actualizarProgreso(
                usuarioRetoId, userSecurity.getUser().getId(), actualizarProgresoDTO));
    }
}
