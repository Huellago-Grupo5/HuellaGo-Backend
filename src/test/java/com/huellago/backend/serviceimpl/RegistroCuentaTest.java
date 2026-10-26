package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistroCuentaTest {
    @Mock UsuarioRepository usuarioRepository;
    @InjectMocks UsuarioServiceImpl servicio;
    UsuarioRegistroDTO valido() { return new UsuarioRegistroDTO("  Carlos  ", " Quispe ", " CUENTA@EXAMPLE.INVALID ", "Registro!2026Seguro"); }
    @Test void normalizaPersisteBcryptYSoloDevuelveDatosPublicos() {
        when(usuarioRepository.saveAndFlush(any())).thenAnswer(i -> { Usuario u = i.getArgument(0); u.setId(22L); return u; });
        var dto = valido(); var respuesta = servicio.registrar(dto);
        assertEquals("cuenta@example.invalid", respuesta.getCorreo()); assertEquals("Carlos", respuesta.getNombres());
        var captor = ArgumentCaptor.forClass(Usuario.class); verify(usuarioRepository).saveAndFlush(captor.capture());
        assertTrue(new BCryptPasswordEncoder().matches(dto.getContrasena(), captor.getValue().getContrasena()));
        assertEquals(0, respuesta.getEcoPuntos()); assertEquals(1, respuesta.getNivel());
        assertFalse(dto.toString().contains(dto.getContrasena()));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "A", "Nombre1"})
    void nombresInvalidosEnServicio(String valor) { var dto=valido(); dto.setNombres(valor); rechazar(dto); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "A", "Apellido1"})
    void apellidosInvalidosEnServicio(String valor) { var dto=valido(); dto.setApellidos(valor); rechazar(dto); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "no-correo", "a@"})
    void correosInvalidosEnServicio(String valor) { var dto=valido(); dto.setCorreo(valor); rechazar(dto); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "corta", "SinSimbolo2026", "sinmayuscula!2026"})
    void clavesInvalidasEnServicio(String valor) { var dto=valido(); dto.setContrasena(valor); rechazar(dto); }
    @Test void rechazaMasDe72BytesUtf8SinTruncamientoBcrypt() { var dto=valido(); dto.setContrasena("Á".repeat(40)+"a1!"); rechazar(dto); }
    void rechazar(UsuarioRegistroDTO dto) {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> servicio.registrar(dto)).getStatusCode().value());
        verifyNoInteractions(usuarioRepository);
    }
    @Test void duplicadoEs409() {
        when(usuarioRepository.existeCorreoNormalizado("cuenta@example.invalid")).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> servicio.registrar(valido())).getStatusCode().value());
        verify(usuarioRepository, never()).saveAndFlush(any());
    }
    @Test void conflictoDeIndiceConcurrenteEs409SinDetallesSql() {
        when(usuarioRepository.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("SQL privado"));
        var error=assertThrows(ResponseStatusException.class, () -> servicio.registrar(valido()));
        assertEquals(409,error.getStatusCode().value()); assertFalse(error.toString().contains("SQL privado"));
    }
}
