package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Insignia;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.UsuarioInsignia;
import com.huellago.backend.repositories.InsigniaRepository;
import com.huellago.backend.repositories.UsuarioInsigniaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsigniaServiceImplTest {
    @Mock InsigniaRepository catalogo;
    @Mock UsuarioInsigniaRepository obtenidas;
    @InjectMocks InsigniaServiceImpl servicio;

    @Test
    void desbloqueaSoloInsigniasCuyoUmbralDelCatalogoSeCumple() {
        Usuario usuario = usuario(30);
        when(catalogo.findAll()).thenReturn(List.of(insignia(1, 10), insignia(2, 50)));
        when(obtenidas.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        var nuevas = servicio.evaluarYDesbloquearInsignias(usuario);

        assertEquals(1, nuevas.size());
        assertEquals(1, nuevas.getFirst().getId());
        assertTrue(nuevas.getFirst().getDesbloqueada());
        assertNotNull(nuevas.getFirst().getFechaObtencion());
        verify(obtenidas).save(argThat(ui -> ui.getUsuario() == usuario
                && ui.getInsignia().getId() == 1));
        verify(obtenidas, times(1)).save(any());
        assertEquals(30, usuario.getEcoPuntos());
    }

    @Test
    void noOtorgaDuplicadoSiYaEstaPersistida() {
        when(catalogo.findAll()).thenReturn(List.of(insignia(1, 10)));
        when(obtenidas.existsByUsuario_IdAndInsignia_Id(22L, 1)).thenReturn(true);

        assertTrue(servicio.evaluarYDesbloquearInsignias(usuario(30)).isEmpty());
        verify(obtenidas, never()).save(any());
    }

    @Test
    void consultaObtenidasYBloqueadasSinEscribirNiDesbloquear() throws Exception {
        Insignia ganada = insignia(1, 10);
        Insignia bloqueada = insignia(2, 50);
        UsuarioInsignia asociacion = new UsuarioInsignia();
        asociacion.setInsignia(ganada);
        asociacion.setUsuario(usuario(30));
        asociacion.setFechaObtencion(LocalDateTime.of(2026, 10, 20, 10, 0));
        when(catalogo.findAll()).thenReturn(List.of(ganada, bloqueada));
        when(obtenidas.findByUsuario_IdOrderByFechaObtencionDesc(22L))
                .thenReturn(List.of(asociacion));

        var resultado = servicio.obtenerInsignias(usuario(30));

        assertEquals(2, resultado.size());
        assertTrue(resultado.get(0).getDesbloqueada());
        assertEquals(asociacion.getFechaObtencion(), resultado.get(0).getFechaObtencion());
        assertFalse(resultado.get(1).getDesbloqueada());
        assertNull(resultado.get(1).getFechaObtencion());
        verify(obtenidas, never()).save(any());
        assertTrue(InsigniaServiceImpl.class.getMethod("obtenerInsignias", Usuario.class)
                .getAnnotation(Transactional.class).readOnly());
    }

    private Usuario usuario(int puntos) {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setEcoPuntos(puntos);
        return usuario;
    }

    private Insignia insignia(int id, int puntos) {
        Insignia insignia = new Insignia();
        insignia.setId(id);
        insignia.setNombre("Insignia " + id);
        insignia.setPuntosRequeridos(puntos);
        return insignia;
    }
}
