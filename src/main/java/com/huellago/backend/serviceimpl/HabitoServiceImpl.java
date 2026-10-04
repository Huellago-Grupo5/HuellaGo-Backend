package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.AlimentacionResiduosDTO;
import com.huellago.backend.dtos.EnergiaDTO;
import com.huellago.backend.dtos.HabitoRespuestaDTO;
import com.huellago.backend.dtos.TransporteDTO;
import com.huellago.backend.entities.CategoriaHabito;
import com.huellago.backend.entities.Habito;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.CategoriaHabitoRepository;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.services.HabitoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.huellago.backend.dtos.ActualizarHabitoDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class HabitoServiceImpl implements HabitoService {

    @Autowired
    HabitoRepository habitoRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    CategoriaHabitoRepository categoriaHabitoRepository;

    @Override
    public List<HabitoRespuestaDTO> registrarTransporte(TransporteDTO dto) {
        validarNoNulo(dto.getUsuarioId(), "usuarioId");
        validarPermitido(dto.getMedio(), Set.of("Bicicleta", "Caminando", "Bus", "Auto", "Moto"), "medio");
        validarPositivo(dto.getKmSemana(), "kmSemana");
        validarPositivo(dto.getDiasSemana(), "diasSemana");

        Usuario usuario = buscarUsuario(dto.getUsuarioId());
        CategoriaHabito categoria = buscarCategoria("Transporte");
        List<HabitoRespuestaDTO> respuestas = new ArrayList<>();
        respuestas.add(guardarHabito(usuario, categoria, "medio", BigDecimal.ZERO, dto.getMedio()));
        respuestas.add(guardarHabito(usuario, categoria, "kmSemana", dto.getKmSemana(), "km/semana"));
        respuestas.add(guardarHabito(usuario, categoria, "diasSemana", BigDecimal.valueOf(dto.getDiasSemana()), "dias/semana"));
        return respuestas;
    }

    @Override
    public List<HabitoRespuestaDTO> registrarEnergia(EnergiaDTO dto) {
        validarNoNulo(dto.getUsuarioId(), "usuarioId");
        validarPermitido(dto.getVivienda(), Set.of("Casa", "Departamento"), "vivienda");
        validarPositivo(dto.getPersonas(), "personas");
        validarPermitido(dto.getFuente(), Set.of("Electricidad", "Gas", "Solar"), "fuente");

        Usuario usuario = buscarUsuario(dto.getUsuarioId());
        CategoriaHabito categoria = buscarCategoria("Energia");
        List<HabitoRespuestaDTO> respuestas = new ArrayList<>();
        respuestas.add(guardarHabito(usuario, categoria, "vivienda", BigDecimal.ZERO, dto.getVivienda()));
        respuestas.add(guardarHabito(usuario, categoria, "personas", BigDecimal.valueOf(dto.getPersonas()), "personas"));
        respuestas.add(guardarHabito(usuario, categoria, "fuente", BigDecimal.ZERO, dto.getFuente()));
        return respuestas;
    }

    @Override
    public List<HabitoRespuestaDTO> registrarAlimentacionResiduos(AlimentacionResiduosDTO dto) {
        validarNoNulo(dto.getUsuarioId(), "usuarioId");
        validarPermitido(dto.getTipo(), Set.of("Vegana", "Vegetariana", "Mixta"), "tipo");
        validarPermitido(dto.getPlasticos(), Set.of("Bajo", "Medio", "Alto"), "plasticos");
        validarNoNulo(dto.getReciclas(), "reciclas");

        Usuario usuario = buscarUsuario(dto.getUsuarioId());
        CategoriaHabito alimentacion = buscarCategoria("Alimentacion");
        CategoriaHabito residuos = buscarCategoria("Residuos");
        List<HabitoRespuestaDTO> respuestas = new ArrayList<>();
        respuestas.add(guardarHabito(usuario, alimentacion, "tipo", BigDecimal.ZERO, dto.getTipo()));
        respuestas.add(guardarHabito(usuario, residuos, "plasticos", BigDecimal.ZERO, dto.getPlasticos()));
        respuestas.add(guardarHabito(usuario, residuos, "reciclas",
                dto.getReciclas() ? BigDecimal.ONE : BigDecimal.ZERO, "boolean"));
        return respuestas;
    }


    @Override
    public HabitoRespuestaDTO actualizarHabito(Long id, ActualizarHabitoDTO dto, Long usuarioId) {

        validarNoNulo(id, "id");
        validarNoNulo(dto, "datos");
        validarNoNulo(usuarioId, "usuarioId");

        Habito habito = habitoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Hábito no encontrado"
                ));

        if (!habito.getUsuario().getId().equals(usuarioId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tienes permiso para modificar este hábito"
            );
        }

        if (dto.getValor() != null) {
            if (dto.getValor().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El valor no puede ser negativo"
                );
            }

            habito.setValor(dto.getValor());
        }

        if (dto.getUnidad() != null && !dto.getUnidad().isBlank()) {
            habito.setUnidad(dto.getUnidad());
        }

        habito.setFechaActualizacion(LocalDateTime.now());

        Habito actualizado = habitoRepository.save(habito);

        return new HabitoRespuestaDTO(
                actualizado.getId(),
                actualizado.getUsuario().getId(),
                actualizado.getCategoria().getId(),
                actualizado.getNombre(),
                actualizado.getValor(),
                actualizado.getUnidad(),
                actualizado.getFechaRegistro(),
                actualizado.getFechaActualizacion()
        );
    }



    private Usuario buscarUsuario(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }
        return usuario;
    }

    private CategoriaHabito buscarCategoria(String nombre) {
        CategoriaHabito categoria = categoriaHabitoRepository.findByNombreIgnoreCase(nombre);
        if (categoria == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría " + nombre + " no encontrada");
        }
        return categoria;
    }

    private HabitoRespuestaDTO guardarHabito(Usuario usuario, CategoriaHabito categoria,
                                             String nombre, BigDecimal valor, String unidad) {
        LocalDateTime ahora = LocalDateTime.now();
        Habito habito = new Habito();
        habito.setUsuario(usuario);
        habito.setCategoria(categoria);
        habito.setNombre(nombre);
        habito.setValor(valor);
        habito.setUnidad(unidad);
        habito.setFechaRegistro(ahora);
        habito.setFechaActualizacion(ahora);

        Habito guardado = habitoRepository.save(habito);
        return new HabitoRespuestaDTO(
                guardado.getId(), guardado.getUsuario().getId(), guardado.getCategoria().getId(),
                guardado.getNombre(), guardado.getValor(), guardado.getUnidad(),
                guardado.getFechaRegistro(), guardado.getFechaActualizacion()
        );
    }

    private void validarPermitido(String valor, Set<String> permitidos, String campo) {
        if (valor == null || permitidos.stream().noneMatch(valor::equalsIgnoreCase)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor inválido para " + campo);
        }
    }

    private void validarNoNulo(Object valor, String campo) {
        if (valor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo " + campo + " es obligatorio");
        }
    }

    private void validarPositivo(Number valor, String campo) {
        validarNoNulo(valor, campo);
        if (valor.doubleValue() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo " + campo + " debe ser positivo");
        }
    }
}
