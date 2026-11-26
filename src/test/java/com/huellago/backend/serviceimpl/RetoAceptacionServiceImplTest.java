package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Reto;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.UsuarioReto;
import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.repositories.UsuarioRetoRepository;
import com.huellago.backend.services.InsigniaService;
import com.huellago.backend.services.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetoAceptacionServiceImplTest {
    @Mock RetoRepository retos;
    @Mock UsuarioRetoRepository asociaciones;
    @Mock UsuarioService usuarios;
    @Mock InsigniaService insignias;
    @InjectMocks RetoServiceImpl servicio;

    @Test
    void aceptaRetoDisponibleParaUsuarioAutenticadoSinRecompensas() {
        Reto reto = retoDisponible();
        Usuario usuario = usuario(22L);
        usuario.setEcoPuntos(10);
        usuario.setNivel(2);
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(reto));
        when(asociaciones.saveAndFlush(any())).thenAnswer(invocacion -> {
            UsuarioReto asociacion = invocacion.getArgument(0);
            asociacion.setId(51);
            return asociacion;
        });

        var respuesta = servicio.aceptarReto(7, usuario);
        ArgumentCaptor<UsuarioReto> guardado = ArgumentCaptor.forClass(UsuarioReto.class);
        verify(asociaciones).saveAndFlush(guardado.capture());
        assertSame(usuario, guardado.getValue().getUsuario());
        assertSame(reto, guardado.getValue().getReto());
        assertEquals("activo", guardado.getValue().getEstado());
        assertEquals(BigDecimal.ZERO, guardado.getValue().getProgreso());
        assertNotNull(guardado.getValue().getFechaAceptacion());
        assertNull(guardado.getValue().getFechaCompletado());
        assertEquals(51, respuesta.getId());
        assertEquals(22L, respuesta.getUsuarioId());
        assertEquals(7, respuesta.getRetoId());
        assertEquals("activo", respuesta.getEstado());
        assertEquals(10, usuario.getEcoPuntos());
        assertEquals(2, usuario.getNivel());
        verifyNoInteractions(usuarios, insignias);
    }

    @Test
    void rechazaDuplicadoSinGuardar() {
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(retoDisponible()));
        when(asociaciones.existsByUsuario_IdAndReto_Id(22L, 7)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.aceptarReto(7, usuario(22L))).getStatusCode());
        verify(asociaciones, never()).saveAndFlush(any());
    }

    @Test
    void retoInexistenteDevuelve404() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> servicio.aceptarReto(999, usuario(22L))).getStatusCode());
        verify(asociaciones, never()).saveAndFlush(any());
    }

    @Test
    void rechazaRetoInactivoFuturoOVencido() {
        Reto inactivo = retoDisponible();
        inactivo.setActivo(false);
        Reto futuro = retoDisponible();
        futuro.setFechaInicio(LocalDate.now().plusDays(1));
        Reto vencido = retoDisponible();
        vencido.setFechaFin(LocalDate.now().minusDays(1));
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(inactivo), Optional.of(futuro), Optional.of(vencido));
        for (int i = 0; i < 3; i++) {
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> servicio.aceptarReto(7, usuario(22L))).getStatusCode());
        }
        verify(asociaciones, never()).saveAndFlush(any());
    }

    @Test
    void usuariosDiferentesPuedenAceptarElMismoReto() {
        when(retos.bloquearParaAceptacion(7)).thenReturn(Optional.of(7));
        when(retos.findById(7)).thenReturn(Optional.of(retoDisponible()));
        when(asociaciones.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        assertEquals(22L, servicio.aceptarReto(7, usuario(22L)).getUsuarioId());
        assertEquals(99L, servicio.aceptarReto(7, usuario(99L)).getUsuarioId());
        verify(asociaciones).existsByUsuario_IdAndReto_Id(22L, 7);
        verify(asociaciones).existsByUsuario_IdAndReto_Id(99L, 7);
        verify(asociaciones, times(2)).saveAndFlush(any());
    }

    @Test
    void listadoOmiteRetosNoDisponibles() {
        Reto disponible = retoDisponible();
        Reto vencido = retoDisponible();
        vencido.setId(8);
        vencido.setFechaFin(LocalDate.now().minusDays(1));
        when(retos.findByActivoTrueOrderByFechaInicioAsc()).thenReturn(List.of(disponible, vencido));
        assertEquals(List.of(7), servicio.listarRetosActivos().stream().map(r -> r.getId()).toList());
    }

    @Test
    void usaBloqueoDeFilaDentroDeTransaccionParaEvitarCarreras() throws Exception {
        assertNotNull(RetoServiceImpl.class.getMethod("aceptarReto", Integer.class, Usuario.class)
                .getAnnotation(Transactional.class));
        Query query = RetoRepository.class.getMethod("bloquearParaAceptacion", Integer.class)
                .getAnnotation(Query.class);
        assertTrue(query.nativeQuery());
        assertTrue(query.value().contains("FOR UPDATE"));
    }

    private Reto retoDisponible() {
        Reto reto = new Reto();
        reto.setId(7);
        reto.setTitulo("Reto de prueba");
        reto.setActivo(true);
        reto.setFechaInicio(LocalDate.now().minusDays(1));
        reto.setFechaFin(LocalDate.now().plusDays(1));
        return reto;
    }

    private Usuario usuario(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }
}
