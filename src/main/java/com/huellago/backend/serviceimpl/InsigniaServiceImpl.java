package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.InsigniaRespuestaDTO;
import com.huellago.backend.entities.Insignia;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.entities.UsuarioInsignia;
import com.huellago.backend.repositories.InsigniaRepository;
import com.huellago.backend.repositories.UsuarioInsigniaRepository;
import com.huellago.backend.services.InsigniaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InsigniaServiceImpl implements InsigniaService {

    @Autowired
    InsigniaRepository insigniaRepository;

    @Autowired
    UsuarioInsigniaRepository usuarioInsigniaRepository;

    @Override
    @Transactional
    public void evaluarYDesbloquearInsignias(Usuario usuario) {
        int ecoPuntos = usuario.getEcoPuntos() == null ? 0 : usuario.getEcoPuntos();

        for (Insignia insignia : insigniaRepository.findAll()) {
            int puntosRequeridos = insignia.getPuntosRequeridos() == null
                    ? Integer.MAX_VALUE : insignia.getPuntosRequeridos();
            if (ecoPuntos >= puntosRequeridos
                    && !usuarioInsigniaRepository.existsByUsuario_IdAndInsignia_Id(
                    usuario.getId(), insignia.getId())) {
                UsuarioInsignia usuarioInsignia = new UsuarioInsignia();
                usuarioInsignia.setUsuario(usuario);
                usuarioInsignia.setInsignia(insignia);
                usuarioInsignia.setFechaObtencion(LocalDateTime.now());
                usuarioInsigniaRepository.save(usuarioInsignia);
            }
        }
    }

    @Override
    @Transactional
    public List<InsigniaRespuestaDTO> obtenerInsignias(Usuario usuario) {
        evaluarYDesbloquearInsignias(usuario);
        return usuarioInsigniaRepository
                .findByUsuario_IdOrderByFechaObtencionDesc(usuario.getId())
                .stream()
                .map(this::convertirADTO)
                .toList();
    }

    private InsigniaRespuestaDTO convertirADTO(UsuarioInsignia usuarioInsignia) {
        Insignia insignia = usuarioInsignia.getInsignia();
        return new InsigniaRespuestaDTO(
                insignia.getId(),
                insignia.getNombre(),
                insignia.getDescripcion(),
                insignia.getImagenUrl(),
                insignia.getPuntosRequeridos(),
                usuarioInsignia.getFechaObtencion()
        );
    }
}
