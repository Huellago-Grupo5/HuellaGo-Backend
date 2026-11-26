package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversionRecomendacionServiceImplTest {
    @Mock UsuarioRecomendacionIARepository asociaciones;
    @Mock RetoRepository retos;
    @Mock UsuarioRetoRepository participaciones;
    @InjectMocks RecomendacionServiceImpl servicio;

    private Usuario usuario(long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEcoPuntos(0);
        usuario.setNivel(1);
        return usuario;
    }

    private void preparar(Integer asociacionId, Usuario usuario) {
        RecomendacionIA recomendacion = new RecomendacionIA();
        recomendacion.setId(8);
        recomendacion.setTitulo("Reducir consumo eléctrico");
        recomendacion.setDescripcion("Apagar luces sin uso");
        UsuarioRecomendacionIA asociacion = new UsuarioRecomendacionIA();
        asociacion.setId(asociacionId);
        asociacion.setUsuario(usuario);
        asociacion.setRecomendacionIA(recomendacion);
        when(asociaciones.bloquearPropia(asociacionId, usuario.getId())).thenReturn(Optional.of(asociacionId));
        when(asociaciones.findById(asociacionId)).thenReturn(Optional.of(asociacion));
        when(retos.saveAndFlush(any())).thenAnswer(invocation -> {
            Reto reto = invocation.getArgument(0);
            reto.setId(40 + asociacionId);
            return reto;
        });
        lenient().when(participaciones.saveAndFlush(any())).thenAnswer(invocation -> {
            UsuarioReto participacion = invocation.getArgument(0);
            participacion.setId(60 + asociacionId);
            return participacion;
        });
    }

    @Test
    void convierteEnParticipacionActivaIndividualSinRecompensas() {
        Usuario usuario = usuario(22);
        preparar(3, usuario);
        var respuesta = servicio.convertirEnReto(3, usuario);
        assertEquals(43, respuesta.retoId());
        assertEquals(63, respuesta.participacionId());
        assertEquals("activo", respuesta.estado());
        assertNotNull(respuesta.fechaAceptacion());
        assertTrue(respuesta.mensaje().contains("convertida"));
        verify(retos).saveAndFlush(argThat(reto -> reto.getTipo() == TipoReto.INDIVIDUAL
                && reto.getRecomendacionOrigen().getId() == 3
                && reto.getPuntosRecompensa() == 0));
        verify(participaciones).saveAndFlush(argThat(participacion ->
                participacion.getUsuario().getId() == 22
                && participacion.getReto().getId() == 43
                && "activo".equals(participacion.getEstado())
                && participacion.getProgreso().signum() == 0));
        assertEquals(0, usuario.getEcoPuntos());
        assertEquals(1, usuario.getNivel());
    }

    @Test
    void rechazaRepeticionInclusoSiParticipacionAnteriorTermino() {
        Usuario usuario = usuario(22);
        when(asociaciones.bloquearPropia(3, 22L)).thenReturn(Optional.of(3));
        when(asociaciones.findById(3)).thenReturn(Optional.of(new UsuarioRecomendacionIA()));
        when(retos.existsByRecomendacionOrigen_Id(3)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.convertirEnReto(3, usuario)).getStatusCode());
        verify(retos, never()).saveAndFlush(any());
    }

    @Test
    void otroUsuarioConvierteSuPropiaAsociacion() {
        Usuario otro = usuario(33);
        preparar(4, otro);
        assertEquals(44, servicio.convertirEnReto(4, otro).retoId());
        verify(asociaciones).bloquearPropia(4, 33L);
    }

    @Test
    void ajenaOInexistenteNoRevelanNiCreanRetos() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> servicio.convertirEnReto(3, usuario(22))).getStatusCode());
        verifyNoInteractions(retos, participaciones);
    }

    @Test
    void falloAlGuardarParticipacionNoOtorgaPuntos() {
        Usuario usuario = usuario(22);
        preparar(3, usuario);
        doThrow(new IllegalStateException("persistencia"))
                .when(participaciones).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> servicio.convertirEnReto(3, usuario));
        assertEquals(0, usuario.getEcoPuntos());
    }
}
