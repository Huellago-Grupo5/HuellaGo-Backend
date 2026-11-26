package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import com.huellago.backend.repositories.UsuarioRecomendacionIARepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.repositories.UsuarioRetoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardEcoPuntosServiceImplTest {
    @Mock UsuarioRepository usuarios;
    @Mock HabitoRepository habitos;
    @Mock HuellaCarbonoRepository huellas;
    @Mock UsuarioRetoRepository participaciones;
    @Mock UsuarioRecomendacionIARepository recomendaciones;
    @InjectMocks DashboardServiceImpl dashboard;

    @Test
    void muestraSaldoPersistidoSoloDelUsuarioConsultado() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setEcoPuntos(35);
        usuario.setNivel(1);
        when(usuarios.findById(22L)).thenReturn(Optional.of(usuario));

        var resultado = dashboard.obtenerDashboard(22L);

        assertEquals(22L, resultado.getUsuarioId());
        assertEquals(35, resultado.getEcoPuntos());
        assertEquals(1, resultado.getNivel());
        verify(usuarios).findById(22L);
    }

    @Test
    void noConsultaSaldoDeOtroUsuarioCuandoNoExisteElAutenticado() {
        when(usuarios.findById(22L)).thenReturn(Optional.empty());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> dashboard.obtenerDashboard(22L));
        verify(usuarios).findById(22L);
    }
}
