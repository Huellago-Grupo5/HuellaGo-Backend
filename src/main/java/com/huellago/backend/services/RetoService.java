package com.huellago.backend.services;

import com.huellago.backend.dtos.RetoRespuestaDTO;
import com.huellago.backend.dtos.UsuarioRetoRespuestaDTO;
import com.huellago.backend.dtos.ActualizarProgresoDTO;
import com.huellago.backend.dtos.RegistrarAvanceDTO;
import com.huellago.backend.dtos.AvanceRetoRespuestaDTO;
import com.huellago.backend.entities.Usuario;

import java.util.List;

public interface RetoService {
    List<RetoRespuestaDTO> listarRetosActivos();
    List<RetoRespuestaDTO> listarRetosDisponibles(Long usuarioId, String clasificacion);
    UsuarioRetoRespuestaDTO aceptarReto(Integer retoId, Usuario usuario);
    List<UsuarioRetoRespuestaDTO> listarRetosActivos(Long usuarioId);
    List<UsuarioRetoRespuestaDTO> listarRetosCompletados(Long usuarioId);
    UsuarioRetoRespuestaDTO registrarAvance(Integer usuarioRetoId, Long usuarioId, RegistrarAvanceDTO dto);
    List<AvanceRetoRespuestaDTO> listarAvances(Integer usuarioRetoId, Long usuarioId);
    UsuarioRetoRespuestaDTO actualizarProgreso(Integer usuarioRetoId, Long usuarioId,
                                                ActualizarProgresoDTO actualizarProgresoDTO);
    UsuarioRetoRespuestaDTO completarReto(Integer usuarioRetoId, Long usuarioId);
}
