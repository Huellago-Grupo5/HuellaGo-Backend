package com.huellago.backend.controller;

import com.huellago.backend.dto.NotificacionesDTO;
import com.huellago.backend.dto.UsuarioPerfilDTO;
import com.huellago.backend.dto.VisibilidadDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin("*")
@RequestMapping("/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    // HU45: PUT /usuarios/perfil
    @PutMapping("/perfil")
    public ResponseEntity<Usuario> actualizarPerfil(@RequestBody UsuarioPerfilDTO dto) {
        Usuario usuarioActualizado = usuarioService.actualizarPerfil(dto);
        if (usuarioActualizado == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(usuarioActualizado, HttpStatus.OK);
    }

    // HU47: PATCH /usuarios/notificaciones
    @PatchMapping("/notificaciones")
    public ResponseEntity<Usuario> actualizarNotificaciones(@RequestBody NotificacionesDTO dto) {
        Usuario usuarioActualizado = usuarioService.cambiarPreferenciaNotificaciones(dto);
        if (usuarioActualizado == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(usuarioActualizado, HttpStatus.OK);
    }

    // HU48: PATCH /usuarios/visibilidad
    @PatchMapping("/visibilidad")
    public ResponseEntity<Usuario> actualizarVisibilidad(@RequestBody VisibilidadDTO dto) {
        Usuario usuarioActualizado = usuarioService.cambiarVisibilidadComunitaria(dto);
        if (usuarioActualizado == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(usuarioActualizado, HttpStatus.OK);
    }
}