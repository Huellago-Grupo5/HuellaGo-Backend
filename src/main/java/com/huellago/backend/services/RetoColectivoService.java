package com.huellago.backend.services;

import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.dtos.RetoColectivoDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.dtos.RegistrarAvanceDTO;
import com.huellago.backend.dtos.AvanceRetoRespuestaDTO;
import com.huellago.backend.entities.Usuario;

import java.util.List;

public interface RetoColectivoService {
    List<RetoColectivoDTO> listarDisponibles();
    List<RetoColectivoDTO> listarDisponibles(Long usuarioId);
    RetoColectivoDTO obtener(Integer retoId);
    RetoColectivoDTO obtener(Integer retoId, Long usuarioId);
    UsuarioRetoRespuestaDTO unirse(Integer retoId, Usuario usuario);
    UsuarioRetoRespuestaDTO actualizarProgreso(Integer participacionId, Long usuarioId,
                                               ActualizarProgresoDTO dto);
    UsuarioRetoRespuestaDTO registrarAvance(Integer participacionId, Long usuarioId, RegistrarAvanceDTO dto);
    List<AvanceRetoRespuestaDTO> listarAvances(Integer participacionId, Long usuarioId);
    void cerrarVencido(Integer retoId);
}
