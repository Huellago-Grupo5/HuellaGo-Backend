package com.huellago.backend.services;

import com.huellago.backend.dtos.HuellaCarbonoRespuestaDTO;
import com.huellago.backend.dtos.HuellaDesgloseDTO;

public interface HuellaCarbonoService {
    public HuellaCarbonoRespuestaDTO calcularHuellaInicial(Long usuarioId);
    public HuellaCarbonoRespuestaDTO buscarPorUsuario(Long usuarioId);
    public HuellaDesgloseDTO obtenerDesglose(Long usuarioId);
}
