package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.InsigniaRespuestaDTO;
import com.huellago.backend.dtos.LogrosRespuestaDTO;
import com.huellago.backend.dtos.NivelRespuestaDTO;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.services.InsigniaService;
import com.huellago.backend.services.LogrosService;
import com.huellago.backend.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogrosServiceImpl implements LogrosService {

    @Autowired
    UsuarioService usuarioService;

    @Autowired
    InsigniaService insigniaService;

    @Override
    public LogrosRespuestaDTO obtenerLogros(Usuario usuario) {
        NivelRespuestaDTO nivel = usuarioService.obtenerNivel(usuario);
        List<InsigniaRespuestaDTO> insignias = insigniaService.obtenerInsignias(usuario);

        return new LogrosRespuestaDTO(
                nivel.getUsuarioId(),
                nivel.getEcoPuntos(),
                nivel.getNivel(),
                nivel.getPuntosEnNivel(),
                nivel.getPuntosParaSiguienteNivel(),
                nivel.getProgresoPorcentaje(),
                insignias
        );
    }
}
