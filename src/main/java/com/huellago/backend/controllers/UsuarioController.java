package com.huellago.backend.controllers;

import com.huellago.backend.dtos.UsuarioRegistroDTO;
import com.huellago.backend.dtos.UsuarioRespuestaDTO;
import com.huellago.backend.dtos.LoginDTO;
import com.huellago.backend.dtos.TokenDTO;
import com.huellago.backend.dtos.RecuperarContrasenaDTO;
import com.huellago.backend.dtos.RestablecerContrasenaDTO;
import com.huellago.backend.services.UsuarioService;
import com.huellago.backend.services.SesionService;
import com.huellago.backend.security.JwtUtilService;
import com.huellago.backend.security.UserSecurity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin("*")
@RequestMapping("/auth")
public class UsuarioController {

    @Autowired
    UsuarioService usuarioService;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    JwtUtilService jwtUtilService;

    @Autowired
    UserDetailsService userDetailsService;

    @Autowired
    SesionService sesionService;

    @PostMapping("/register")
    public ResponseEntity<UsuarioRespuestaDTO> registrar(@RequestBody UsuarioRegistroDTO usuarioRegistroDTO) {
        return new ResponseEntity<>(usuarioService.registrar(usuarioRegistroDTO), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenDTO> login(@RequestBody LoginDTO loginDTO) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    loginDTO.getCorreo(), loginDTO.getContrasena()));
        } catch (BadCredentialsException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        UserSecurity userSecurity = (UserSecurity) userDetailsService
                .loadUserByUsername(loginDTO.getCorreo());
        String token = jwtUtilService.generateToken(userSecurity);
        sesionService.registrarSesion(userSecurity, token);
        TokenDTO tokenDTO = new TokenDTO(
                token,
                userSecurity.getUser().getId(),
                userSecurity.getUsername()
        );
        return new ResponseEntity<>(tokenDTO, HttpStatus.OK);
    }

    @PostMapping("/recuperar-contrasena")
    public ResponseEntity<TokenDTO> recuperarContrasena(
            @RequestBody RecuperarContrasenaDTO recuperarContrasenaDTO) {
        return new ResponseEntity<>(
                usuarioService.solicitarRecuperacion(recuperarContrasenaDTO), HttpStatus.OK);
    }

    @PostMapping("/restablecer-contrasena")
    public ResponseEntity<Void> restablecerContrasena(
            @RequestBody RestablecerContrasenaDTO restablecerContrasenaDTO) {
        usuarioService.restablecerContrasena(restablecerContrasenaDTO);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
