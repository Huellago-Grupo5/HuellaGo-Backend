package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CambioContrasenaServiceImplTest {
    @Mock UsuarioRepository usuarioRepository;
    @InjectMocks UsuarioServiceImpl service;

    @Test
    void cambiarContrasenaVerificaLaActualYGuardaLaNuevaCodificada() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Usuario usuario = new Usuario(); usuario.setId(77L);
        usuario.setContrasena(encoder.encode("ActualSegura123!"));
        when(usuarioRepository.findById(77L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        service.cambiarContrasena(usuario, "ActualSegura123!", "NuevaSegura456!");

        assertTrue(encoder.matches("NuevaSegura456!", usuario.getContrasena()));
        assertFalse(encoder.matches("ActualSegura123!", usuario.getContrasena()));
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void contrasenaActualIncorrectaNoPersisteCambios() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Usuario usuario = new Usuario(); usuario.setId(77L);
        usuario.setContrasena(encoder.encode("ActualSegura123!"));
        when(usuarioRepository.findById(77L)).thenReturn(Optional.of(usuario));

        assertThrows(ResponseStatusException.class,
                () -> service.cambiarContrasena(usuario, "incorrecta", "NuevaSegura456!"));
        verify(usuarioRepository, never()).save(any());
    }
}
