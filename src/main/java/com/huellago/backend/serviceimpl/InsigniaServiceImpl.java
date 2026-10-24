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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InsigniaServiceImpl implements InsigniaService {

    @Autowired
    InsigniaRepository insigniaRepository;

    @Autowired
    UsuarioInsigniaRepository usuarioInsigniaRepository;

    @Override
    @Transactional
    public List<InsigniaRespuestaDTO> evaluarYDesbloquearInsignias(Usuario usuario) {
        int ecoPuntos = usuario.getEcoPuntos() == null ? 0 : usuario.getEcoPuntos();
        List<InsigniaRespuestaDTO> nuevas = new ArrayList<>();

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
                nuevas.add(convertirADTO(usuarioInsigniaRepository.save(usuarioInsignia)));
            }
        }
        return nuevas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InsigniaRespuestaDTO> obtenerInsignias(Usuario usuario) {
        Map<Integer, UsuarioInsignia> obtenidas = usuarioInsigniaRepository
                .findByUsuario_IdOrderByFechaObtencionDesc(usuario.getId()).stream()
                .collect(Collectors.toMap(ui -> ui.getInsignia().getId(),
                        Function.identity(), (primera, repetida) -> primera));
        return insigniaRepository.findAll().stream()
                .map(insignia -> {
                    UsuarioInsignia obtenida = obtenidas.get(insignia.getId());
                    return new InsigniaRespuestaDTO(insignia.getId(), insignia.getNombre(),
                            insignia.getDescripcion(), insignia.getImagenUrl(),
                            insignia.getPuntosRequeridos(),
                            obtenida == null ? null : obtenida.getFechaObtencion(),
                            obtenida != null);
                })
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
                usuarioInsignia.getFechaObtencion(),
                true
        );
    }
}
