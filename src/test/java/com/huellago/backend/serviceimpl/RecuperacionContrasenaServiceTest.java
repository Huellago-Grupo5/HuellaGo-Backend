package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.services.CorreoRecuperacionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecuperacionContrasenaServiceTest {
    @Mock RecuperacionContrasenaRepository recuperaciones;
    @Mock UsuarioRepository usuarios;
    @Mock SesionRepository sesiones;
    @Mock CorreoRecuperacionService correo;
    @Mock jakarta.persistence.EntityManager entityManager;
    @Mock com.huellago.backend.services.LimiteRecuperacionService limite;
    @InjectMocks RecuperacionContrasenaServiceImpl servicio;
    final String secreto = "a".repeat(43);
    final String clave = "NuevaClave!2026";
    Usuario usuario;
    RecuperacionContrasena fila;
    @BeforeEach void datos() {
        usuario = new Usuario(); usuario.setId(22L); usuario.setCorreo("cuenta@example.invalid"); usuario.setContrasena("anterior");
        fila = new RecuperacionContrasena(); fila.setUsuario(usuario); fila.setTokenHash(RecuperacionContrasenaServiceImpl.hash(secreto));
        fila.setCorreoDestino(usuario.getCorreo()); fila.setCreadoEn(Instant.now()); fila.setExpiraEn(Instant.now().plusSeconds(900));
    }
    void tokenDisponible() {
        when(recuperaciones.buscarUsuario(fila.getTokenHash())).thenReturn(Optional.of(22L));
        when(usuarios.bloquearParaRecomendaciones(22L)).thenReturn(Optional.of(22L));
        when(recuperaciones.bloquear(fila.getTokenHash())).thenReturn(Optional.of(fila));
        when(usuarios.findById(22L)).thenReturn(Optional.of(usuario));
    }
    @Test void solicitudSoloEnviaSecretoPorCorreoYGuardaHashConExpiracion() {
        when(usuarios.findByCorreoNormalizado(usuario.getCorreo())).thenReturn(usuario);
        when(usuarios.bloquearParaRecomendaciones(22L)).thenReturn(Optional.of(22L));
        var respuesta = servicio.solicitar(new RecuperarContrasenaDTO(usuario.getCorreo()));
        var token = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq(usuario.getCorreo()), token.capture());
        var registro = ArgumentCaptor.forClass(RecuperacionContrasena.class);
        verify(recuperaciones).saveAndFlush(registro.capture());
        assertEquals(43, token.getValue().length());
        assertEquals(RecuperacionContrasenaServiceImpl.hash(token.getValue()), registro.getValue().getTokenHash());
        assertNotEquals(token.getValue(), registro.getValue().getTokenHash());
        assertEquals(900, java.time.Duration.between(registro.getValue().getCreadoEn(), registro.getValue().getExpiraEn()).getSeconds());
        assertFalse(respuesta.toString().contains(token.getValue()));
        assertEquals(1, RecuperacionRespuestaDTO.class.getRecordComponents().length);
    }
    @Test void correoInexistenteEs404SinTokenNiEnvio() {
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> servicio.solicitar(new RecuperarContrasenaDTO(usuario.getCorreo()))).getStatusCode().value());
        verify(recuperaciones, never()).saveAndFlush(any()); verify(correo, never()).enviar(any(), any());
    }
    @Test void smtpAusenteNoGeneraSecretos() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)).when(correo).verificarConfiguracion();
        assertThrows(ResponseStatusException.class, () -> servicio.solicitar(new RecuperarContrasenaDTO(usuario.getCorreo())));
        verifyNoInteractions(usuarios, recuperaciones, sesiones);
    }
    @Test void falloDeEnvioPropagaErrorControlado() {
        when(usuarios.findByCorreoNormalizado(usuario.getCorreo())).thenReturn(usuario);
        when(usuarios.bloquearParaRecomendaciones(22L)).thenReturn(Optional.of(22L));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Correo no disponible"))
                .when(correo).enviar(any(), any());
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> servicio.solicitar(new RecuperarContrasenaDTO(usuario.getCorreo()))).getStatusCode().value());
    }
    @Test void tokenValidoActualizaBcryptConsumeYRevocaTodasLasSesiones() {
        tokenDisponible(); var s1 = new Sesion(); s1.setActiva(true); var s2 = new Sesion(); s2.setActiva(true);
        when(sesiones.findByUsuario_IdAndActivaTrue(22L)).thenReturn(List.of(s1, s2));
        servicio.restablecer(new RestablecerContrasenaDTO(secreto, clave));
        assertTrue(new BCryptPasswordEncoder().matches(clave, usuario.getContrasena()));
        assertNotEquals(clave, usuario.getContrasena()); assertNotNull(fila.getConsumidoEn());
        assertFalse(s1.getActiva()); assertFalse(s2.getActiva()); verify(sesiones).saveAll(List.of(s1, s2));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> servicio.restablecer(new RestablecerContrasenaDTO(secreto, clave))).getStatusCode().value());
        verify(usuarios, times(1)).saveAndFlush(usuario);
    }
    @Test void tokenExpiradoNoActualiza() { tokenDisponible(); fila.setExpiraEn(Instant.now().minusSeconds(1)); rechazar(); }
    @Test void tokenConsumidoNoActualiza() { tokenDisponible(); fila.setConsumidoEn(Instant.now()); rechazar(); }
    @Test void tokenDeCorreoAnteriorNoActualiza() { tokenDisponible(); fila.setCorreoDestino("anterior@example.invalid"); rechazar(); }
    @Test void tokenAleatorioInexistenteNoActualiza() { rechazar(); }
    @Test void limiteRechazadoNoConsultaCuentaNoInvalidaTokenNiContactaCorreo() {
        doThrow(new com.huellago.backend.security.LimiteRecuperacionException(60)).when(limite).reservar(usuario.getCorreo());
        assertEquals(429, assertThrows(ResponseStatusException.class,
                () -> servicio.solicitar(new RecuperarContrasenaDTO(usuario.getCorreo()))).getStatusCode().value());
        verifyNoInteractions(usuarios, recuperaciones, correo, sesiones);
    }
    @Test void jwtFirmadoConDisenoLegadoTambienEsRechazadoSinConsultarPersistencia() {
        var claveLegada = io.jsonwebtoken.Jwts.SIG.HS256.key().build();
        String legado = io.jsonwebtoken.Jwts.builder().subject(usuario.getCorreo()).claim("purpose", "PASSWORD_RESET")
                .expiration(new Date(System.currentTimeMillis()+900000)).signWith(claveLegada).compact();
        assertThrows(ResponseStatusException.class, () -> servicio.restablecer(new RestablecerContrasenaDTO(legado,clave)));
        verifyNoInteractions(recuperaciones,usuarios,sesiones);
    }
    void rechazar() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> servicio.restablecer(new RestablecerContrasenaDTO(secreto, clave))).getStatusCode().value());
        verify(usuarios, never()).saveAndFlush(any()); verify(sesiones, never()).saveAll(any());
    }
    @ParameterizedTest @ValueSource(strings = {"abc", "eyJhbGciOiJIUzI1NiJ9.legacy.signature", "a+b", ""})
    void tokenMalformadoOJwtAntiguoNoEsAceptado(String token) {
        assertThrows(ResponseStatusException.class, () -> servicio.restablecer(new RestablecerContrasenaDTO(token, clave)));
        verifyNoInteractions(recuperaciones, usuarios, sesiones);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "corta", "solominusculas2026!", "SinSimbolo2026", "SinNumero!Larga", "Con espacios!2026"})
    void contrasenaInvalidaNoConsultaTokens(String password) {
        assertThrows(ResponseStatusException.class, () -> servicio.restablecer(new RestablecerContrasenaDTO(secreto, password)));
        verifyNoInteractions(recuperaciones, usuarios, sesiones);
    }
}
