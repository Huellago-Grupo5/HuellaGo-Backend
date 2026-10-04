package com.huellago.backend.controller;

import com.huellago.backend.dto.UsuarioRegistroDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin("*")
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UsuarioService usuarioService;

    @PostMapping("/register")
    public ResponseEntity<Usuario> registrar(@RequestBody UsuarioRegistroDTO dto) {
        Usuario nuevoUsuario = usuarioService.registrar(dto);
        if (nuevoUsuario == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(nuevoUsuario, HttpStatus.CREATED);
    }
}