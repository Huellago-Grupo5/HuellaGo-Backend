package com.huellago.backend.services;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.dtos.ConversionRecomendacionDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.dtos.RecomendacionCompletadaDTO;
import com.huellago.backend.dtos.RecomendacionPersonalizadaDTO;
import com.huellago.backend.dtos.EscenarioImpactoIADTO;
import java.util.List;

public interface RecomendacionService {
    GeneracionRecomendacionesDTO generar(Usuario usuarioAutenticado);
    ConversionRecomendacionDTO convertirEnReto(Integer usuarioRecomendacionId, Usuario usuarioAutenticado);
    RecomendacionCompletadaDTO completar(Integer usuarioRecomendacionId, Usuario usuarioAutenticado);
    List<RecomendacionCompletadaDTO> historialCompletadas(Usuario usuarioAutenticado);
    List<RecomendacionPersonalizadaDTO> pendientes(Usuario usuarioAutenticado, String categoria);
    List<RecomendacionPersonalizadaDTO> pendientes(Usuario usuarioAutenticado, String categoria,
            EscenarioImpactoIADTO escenario);
}
