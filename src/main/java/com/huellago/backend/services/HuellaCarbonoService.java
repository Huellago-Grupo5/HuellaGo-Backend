package com.huellago.backend.services;

import com.huellago.backend.dtos.HuellaCarbonoRespuestaDTO;
import com.huellago.backend.dtos.HuellaDesgloseDTO;

import java.util.List;

public interface HuellaCarbonoService {
    public HuellaCarbonoRespuestaDTO calcularHuellaInicial(Long usuarioId);
    public HuellaCarbonoRespuestaDTO buscarPorUsuario(Long usuarioId);
    public HuellaDesgloseDTO obtenerDesglose(Long usuarioId);
    public List<HuellaCarbonoRespuestaDTO> obtenerHistorial(Long usuarioId);
}
