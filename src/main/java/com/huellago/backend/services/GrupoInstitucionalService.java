package com.huellago.backend.services;

import com.huellago.backend.dtos.GrupoInstitucionalDTO;
import java.util.List;

public interface GrupoInstitucionalService {
    GrupoInstitucionalDTO.Invitacion invitar(Long administradorId, Long institucionId);
    GrupoInstitucionalDTO.Afiliacion afiliar(Long usuarioId, String codigo);
    GrupoInstitucionalDTO.Grupo crear(Long administradorId, Long institucionId, GrupoInstitucionalDTO.Datos datos);
    List<GrupoInstitucionalDTO.Grupo> listar(Long administradorId, Long institucionId);
    GrupoInstitucionalDTO.Grupo actualizar(Long administradorId, Long institucionId, Long grupoId, GrupoInstitucionalDTO.Datos datos);
    void eliminar(Long administradorId, Long institucionId, Long grupoId);
    GrupoInstitucionalDTO.Afiliacion asignar(Long administradorId, Long institucionId, Long grupoId, Long miembroId);
    GrupoInstitucionalDTO.Afiliacion desasignar(Long administradorId, Long institucionId, Long grupoId, Long miembroId);
    GrupoInstitucionalDTO.Ranking ranking(Long administradorId, Long institucionId);
}
