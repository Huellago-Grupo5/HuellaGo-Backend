package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioNivelServiceImplTest {
    @Mock UsuarioRepository usuarios;
    @InjectMocks UsuarioServiceImpl servicio;

    @Test
    void consultaLosLimitesDeLaFormulaActualSinModificarLaCuenta() {
        int[][] casos = {
                {0, 1, 100, 0},
                {30, 1, 70, 30},
                {99, 1, 1, 99},
                {100, 2, 100, 0},
                {199, 2, 1, 99},
                {200, 3, 100, 0}
        };
        for (int[] caso : casos) {
            Usuario usuario = usuario(caso[0]);
            var nivel = servicio.obtenerNivel(usuario);
            assertEquals(22L, nivel.getUsuarioId());
            assertEquals(caso[0], nivel.getEcoPuntos());
            assertEquals(caso[1], nivel.getNivel());
            assertEquals(caso[2], nivel.getPuntosParaSiguienteNivel());
            assertEquals(caso[3], nivel.getPuntosEnNivel());
            assertEquals(caso[3], nivel.getProgresoPorcentaje());
        }
        verify(usuarios, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void persisteElNivelDerivadoDePuntosSinConsumirNiReiniciarElSaldo() {
        Usuario usuario = usuario(120);
        usuario.setNivel(1);
        when(usuarios.save(usuario)).thenAnswer(invocacion -> invocacion.getArgument(0));

        Usuario guardado = servicio.actualizarNivel(usuario);

        assertEquals(120, guardado.getEcoPuntos());
        assertEquals(2, guardado.getNivel());
        verify(usuarios).save(usuario);
    }

    @Test
    void recalcularDosVecesNoIncrementaElNivelDeNuevo() {
        Usuario usuario = usuario(100);
        usuario.setNivel(1);
        when(usuarios.save(usuario)).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.actualizarNivel(usuario);
        servicio.actualizarNivel(usuario);

        assertEquals(100, usuario.getEcoPuntos());
        assertEquals(2, usuario.getNivel());
        verify(usuarios, org.mockito.Mockito.times(2)).save(usuario);
    }

    private Usuario usuario(int puntos) {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setEcoPuntos(puntos);
        return usuario;
    }
}
