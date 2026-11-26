package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioRankingComunitarioServiceImplTest {
    @Mock UsuarioRepository repositorio;
    @InjectMocks UsuarioServiceImpl servicio;

    @Test void puntosDescendentesYEmpatesPorIdConPosicionPropia() {
        var participantes = List.of(
                fila(5L, "Ana", 80, 2), fila(7L, " Luis ", 40, 1),
                fila(70L, "Usuario 70", 40, 1), fila(90L, "Sin puntos", 0, 1));
        when(repositorio.listarRankingComunitario()).thenReturn(participantes);

        var resultado = servicio.obtenerRankingComunitario(usuario(70L));

        assertEquals(4, resultado.participantes().size());
        assertEquals(3, resultado.miPosicion());
        assertEquals(List.of(1, 2, 3, 4), resultado.participantes().stream()
                .map(p -> p.posicion()).toList());
        assertEquals(List.of(80, 40, 40, 0), resultado.participantes().stream()
                .map(p -> p.ecoPuntos()).toList());
        assertEquals("Luis", resultado.participantes().get(1).nombreVisible());
        assertNull(resultado.mensaje());
        assertThrows(UnsupportedOperationException.class,
                () -> resultado.participantes().clear());
    }

    @Test void privacidadSeReflejaEnLaSiguienteConsultaYSinParticipantesHayMensaje() {
        var visibleAntes = List.of(fila(70L, "Usuario 70", 20, 1));
        when(repositorio.listarRankingComunitario()).thenReturn(
                visibleAntes, List.of());

        var visible = servicio.obtenerRankingComunitario(usuario(70L));
        var privado = servicio.obtenerRankingComunitario(usuario(70L));

        assertEquals(1, visible.miPosicion());
        assertNotNull(visible.mensaje());
        assertTrue(privado.participantes().isEmpty());
        assertNull(privado.miPosicion());
        assertNotNull(privado.mensaje());
        verify(repositorio, times(2)).listarRankingComunitario();
    }

    @Test void consultaSoloColumnasPublicasYFiltraPrivadosEnRepositorio() throws Exception {
        var metodo = UsuarioRepository.class.getMethod("listarRankingComunitario");
        String consulta = metodo.getAnnotation(Query.class).value();
        assertTrue(consulta.contains("u.visibilidadComunidad = true"));
        assertTrue(consulta.contains("u.ecoPuntos desc, u.id asc"));
        assertFalse(consulta.contains("correo"));
        assertFalse(consulta.contains("contrasena"));
        assertFalse(consulta.contains("habitos"));
    }

    @Test void exigeIdentidadAutenticada() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> servicio.obtenerRankingComunitario(null)).getStatusCode());
        verifyNoInteractions(repositorio);
    }

    private Usuario usuario(Long id) {
        var usuario = new Usuario(); usuario.setId(id); return usuario;
    }

    private UsuarioRepository.FilaRanking fila(Long id, String nombre, Integer puntos, Integer nivel) {
        var fila = mock(UsuarioRepository.FilaRanking.class);
        lenient().when(fila.getId()).thenReturn(id);
        lenient().when(fila.getNombres()).thenReturn(nombre);
        lenient().when(fila.getEcoPuntos()).thenReturn(puntos);
        lenient().when(fila.getNivel()).thenReturn(nivel);
        return fila;
    }
}
