package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.SeleccionObjetivosAmbientalesDTO;
import com.huellago.backend.entities.ObjetivoAmbiental;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.UsuarioObjetivoAmbiental;
import com.huellago.backend.repositories.ObjetivoAmbientalRepository;
import com.huellago.backend.repositories.UsuarioObjetivoAmbientalRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ObjetivoAmbientalServiceImplTest {

    @Mock
    ObjetivoAmbientalRepository objetivos;

    @Mock
    UsuarioObjetivoAmbientalRepository selecciones;

    @Mock
    UsuarioRepository usuarios;

    @InjectMocks
    ObjetivoAmbientalServiceImpl service;

    @Test
    void actualizaSeleccionConObjetivoCompartidoSinInsertarloDosVeces() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario@huellago.com");
        when(usuarios.findByCorreo(usuario.getCorreo())).thenReturn(usuario);

        Map<Long, ObjetivoAmbiental> catalogo = Map.of(
                1L, objetivo(1L), 2L, objetivo(2L), 3L, objetivo(3L), 4L, objetivo(4L));
        when(objetivos.findAllById(anyList())).thenAnswer(invocacion ->
                ((List<Long>) invocacion.getArgument(0)).stream().map(catalogo::get).toList());

        List<UsuarioObjetivoAmbiental> persistidas = new ArrayList<>();
        when(selecciones.findByUsuarioId(22L)).thenAnswer(invocacion -> new ArrayList<>(persistidas));
        org.mockito.Mockito.doAnswer(invocacion -> {
            persistidas.removeAll(invocacion.getArgument(0));
            return null;
        }).when(selecciones).deleteAll(anyList());
        org.mockito.Mockito.doAnswer(invocacion -> {
            persistidas.addAll(invocacion.getArgument(0));
            return invocacion.getArgument(0);
        }).when(selecciones).saveAll(anyList());

        guardar(List.of(1L, 2L, 3L), usuario, catalogo, persistidas);
        guardar(List.of(2L, 4L), usuario, catalogo, persistidas);
        guardar(List.of(2L, 4L), usuario, catalogo, persistidas);

        assertEquals(List.of(2L, 4L), persistidas.stream()
                .map(asociacion -> asociacion.getObjetivo().getId()).toList());
        verify(selecciones, atLeastOnce()).flush();
        verify(selecciones, times(2)).saveAll(anyList());
    }

    @Test
    void objetivoInexistenteNoEliminaLaSeleccionGuardada() {
        Usuario usuario = new Usuario();
        usuario.setId(22L);
        usuario.setCorreo("usuario@huellago.com");
        when(usuarios.findByCorreo(usuario.getCorreo())).thenReturn(usuario);
        when(objetivos.findAllById(List.of(2L, 99999L))).thenReturn(List.of(objetivo(2L)));

        SeleccionObjetivosAmbientalesDTO dto = new SeleccionObjetivosAmbientalesDTO();
        dto.setObjetivoIds(List.of(2L, 99999L));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.actualizarSeleccion(usuario.getCorreo(), dto));

        assertEquals(404, error.getStatusCode().value());
        verify(selecciones, never()).findByUsuarioId(22L);
        verify(selecciones, never()).deleteAll(anyList());
        verify(selecciones, never()).saveAll(anyList());
    }

    private void guardar(List<Long> ids, Usuario usuario, Map<Long, ObjetivoAmbiental> catalogo,
                         List<UsuarioObjetivoAmbiental> persistidas) {
        SeleccionObjetivosAmbientalesDTO dto = new SeleccionObjetivosAmbientalesDTO();
        dto.setObjetivoIds(ids);
        service.actualizarSeleccion(usuario.getCorreo(), dto);
        assertEquals(ids, persistidas.stream()
                .map(asociacion -> asociacion.getObjetivo().getId()).sorted().toList());
    }

    private ObjetivoAmbiental objetivo(Long id) {
        return new ObjetivoAmbiental(id, "Objetivo " + id, "Descripcion");
    }
}
