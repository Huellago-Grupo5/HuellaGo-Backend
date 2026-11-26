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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        List<String> categorias = List.of("Transporte", "Energía", "Alimentación", "Residuos");
        Map<String, Long> habitosPorCategoria = new LinkedHashMap<>();
        categorias.forEach(categoria -> habitosPorCategoria.put(categoria, 0L));
        Map<String, String> nombresCanonicos = new LinkedHashMap<>();
        categorias.forEach(categoria -> nombresCanonicos.put(ValidacionHabitosIniciales.normalizar(categoria), categoria));
        Map<String, Long> conteos = new java.util.TreeMap<>();
        long total = 0;
        for (var fila : habitoRepository.contarPorCategoria(usuarioId)) {
            total += fila.getCantidad();
            if (fila.getNombre() == null) continue;
            String normalizado = ValidacionHabitosIniciales.normalizar(fila.getNombre());
            String nombre = nombresCanonicos.getOrDefault(normalizado, normalizado);
            conteos.merge(nombre, fila.getCantidad(), Long::sum);
        }
        categorias.forEach(categoria -> habitosPorCategoria.put(categoria, conteos.getOrDefault(categoria, 0L)));
        // TreeMap fija el desempate por nombre; agrupa variantes historicas antes de comparar.
        String dominante = conteos.entrySet().stream()
                .max(java.util.Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey, java.util.Comparator.reverseOrder()))
                .map(Map.Entry::getKey).orElse(null);

        return new DashboardDTO(
                usuario.getId(),
                usuario.getEcoPuntos(),
                usuario.getNivel(),
                total,
                huellaCarbonoRepository.buscarHuellaActual_SQL(usuarioId),
                usuarioRetoRepository.contarRetosActivosUsuario(usuarioId),
                usuarioRecomendacionIARepository.contarRecomendacionesPendientesUsuario(usuarioId),
                habitosPorCategoria,
                dominante,
                usuarioRetoRepository.contarRetosCompletadosUsuario_SQL(usuarioId),
                usuarioRecomendacionIARepository.contarRecomendacionesCompletadasUsuario_SQL(usuarioId)
        );
    }
}
