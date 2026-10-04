package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.DashboardDTO;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import com.huellago.backend.repositories.RetoRepository;
import com.huellago.backend.repositories.UsuarioRecomendacionIARepository;
import com.huellago.backend.repositories.UsuarioRetoRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.services.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    HabitoRepository habitoRepository;

    @Autowired
    HuellaCarbonoRepository huellaCarbonoRepository;

    @Autowired
    RetoRepository retoRepository;

    @Autowired
    UsuarioRetoRepository usuarioRetoRepository;

    @Autowired
    UsuarioRecomendacionIARepository usuarioRecomendacionIARepository;

    @Override
    public DashboardDTO obtenerDashboard(Long usuarioId) {
        var usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        return new DashboardDTO(
                usuario.getId(),
                usuario.getEcoPuntos(),
                usuario.getNivel(),
                habitoRepository.contarHabitosUsuario(usuarioId),
                huellaCarbonoRepository.buscarHuellaActual_SQL(usuarioId),
                usuarioRetoRepository.contarRetosActivosUsuario(usuarioId),
                usuarioRecomendacionIARepository.contarRecomendacionesPendientesUsuario(usuarioId)
        );
    }
}
