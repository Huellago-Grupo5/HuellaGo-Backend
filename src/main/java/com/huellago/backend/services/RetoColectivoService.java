package com.huellago.backend.services;

import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.dtos.RetoColectivoDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.entities.Usuario;

import java.util.List;

public interface RetoColectivoService {
    List<RetoColectivoDTO> listarDisponibles();
    RetoColectivoDTO obtener(Integer retoId);
    UsuarioRetoRespuestaDTO unirse(Integer retoId, Usuario usuario);
    UsuarioRetoRespuestaDTO actualizarProgreso(Integer participacionId, Long usuarioId,
                                               ActualizarProgresoDTO dto);
    void cerrarVencido(Integer retoId);
}
