package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.EcoPuntosAccionDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EcoPuntosAccionServiceImplTest {
    @Mock UsuarioRepository usuarios;
    @InjectMocks UsuarioServiceImpl servicio;

    @Test
    void accionAutodeclaradaRepetidaNoAumentaSaldo() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setEcoPuntos(20);
        usuario.setNivel(1);

        for (int intento = 0; intento < 3; intento++) {
            var error = assertThrows(ResponseStatusException.class,
                    () -> servicio.otorgarEcoPuntos(usuario, new EcoPuntosAccionDTO("RECICLAR")));
            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
            assertEquals(20, usuario.getEcoPuntos());
            assertEquals(1, usuario.getNivel());
        }
        verifyNoInteractions(usuarios);
    }

    @Test
    void accionesSinVerificacionNoConcedenPuntosAunqueSeanDistintas() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setEcoPuntos(20);
        for (String accion : new String[]{"USAR_BICICLETA", "AHORRAR_ENERGIA", "RECICLAR"}) {
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> servicio.otorgarEcoPuntos(usuario, new EcoPuntosAccionDTO(accion)))
                    .getStatusCode());
        }
        assertEquals(20, usuario.getEcoPuntos());
        verifyNoInteractions(usuarios);
    }

    @Test
    void accionOmitidaSeRechazaSinCambiarPuntos() {
        Usuario usuario = new Usuario();
        usuario.setEcoPuntos(20);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> servicio.otorgarEcoPuntos(usuario, new EcoPuntosAccionDTO(null)))
                .getStatusCode());
        assertEquals(20, usuario.getEcoPuntos());
        verifyNoInteractions(usuarios);
    }
}
