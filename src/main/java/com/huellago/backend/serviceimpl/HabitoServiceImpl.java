package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.AlimentacionResiduosDTO;
import com.huellago.backend.dtos.AlimentacionResiduosRespuestaDTO;
import com.huellago.backend.dtos.EnergiaDTO;
import com.huellago.backend.dtos.EnergiaRespuestaDTO;
import com.huellago.backend.dtos.HabitoRespuestaDTO;
import com.huellago.backend.dtos.TransporteDTO;
import com.huellago.backend.dtos.TransporteRespuestaDTO;
import com.huellago.backend.dtos.ActualizarHabitoDTO;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.Locale;

@Service
public class HabitoServiceImpl implements HabitoService {

    @Autowired
    HabitoRepository habitoRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    CategoriaHabitoRepository categoriaHabitoRepository;

    @Override
    @Transactional
    public TransporteRespuestaDTO registrarTransporte(TransporteDTO dto) {
        validarNoNulo(dto.getUsuarioId(), "usuarioId");
        validarPermitido(dto.getMedio(), Set.of("Bicicleta", "Caminando", "Bus", "Auto", "Moto"), "medio");
        validarPositivo(dto.getKmSemana(), "kmSemana");
        validarPositivo(dto.getDiasSemana(), "diasSemana");

        Usuario usuario = buscarUsuario(dto.getUsuarioId());
        CategoriaHabito categoria = buscarCategoria("Transporte");
        validarCategoriaNoRegistrada(usuario.getId(), categoria, "Transporte");
        guardarHabito(usuario, categoria, "medio", BigDecimal.ZERO, dto.getMedio());
        guardarHabito(usuario, categoria, "kmSemana", dto.getKmSemana(), "km/semana");
        guardarHabito(usuario, categoria, "diasSemana", BigDecimal.valueOf(dto.getDiasSemana()), "dias/semana");

        return new TransporteRespuestaDTO(
                usuario.getId(),
                categoria.getId(),
                dto.getMedio(),
                dto.getKmSemana(),
                dto.getDiasSemana()
        );
    }

    @Override
    @Transactional
    public EnergiaRespuestaDTO registrarEnergia(EnergiaDTO dto) {
        validarNoNulo(dto.getUsuarioId(), "usuarioId");
        validarPermitido(dto.getVivienda(), Set.of("Casa", "Departamento"), "vivienda");
        validarPositivo(dto.getPersonas(), "personas");
        validarPermitido(dto.getFuente(), Set.of("Electricidad", "Gas", "Solar"), "fuente");

        Usuario usuario = buscarUsuario(dto.getUsuarioId());
        CategoriaHabito categoria = buscarCategoria("Energía");
        validarCategoriaNoRegistrada(usuario.getId(), categoria, "Energía");
        guardarHabito(usuario, categoria, "vivienda", BigDecimal.ZERO, dto.getVivienda());
        guardarHabito(usuario, categoria, "personas", BigDecimal.valueOf(dto.getPersonas()), "personas");
        guardarHabito(usuario, categoria, "fuente", BigDecimal.ZERO, dto.getFuente());

        return new EnergiaRespuestaDTO(
                usuario.getId(),
                categoria.getId(),
                dto.getVivienda(),
                dto.getPersonas(),
                dto.getFuente()
        );
    }

    @Override
    @Transactional
    public AlimentacionResiduosRespuestaDTO registrarAlimentacionResiduos(AlimentacionResiduosDTO dto) {
        validarNoNulo(dto.getUsuarioId(), "usuarioId");
        validarPermitido(dto.getTipo(), Set.of("Vegana", "Vegetariana", "Mixta"), "tipo");
        validarPermitido(dto.getPlasticos(), Set.of("Bajo", "Medio", "Alto"), "plasticos");
        validarNoNulo(dto.getReciclas(), "reciclas");

        Usuario usuario = buscarUsuario(dto.getUsuarioId());
        CategoriaHabito alimentacion = buscarCategoria("Alimentación");
        CategoriaHabito residuos = buscarCategoria("Residuos");
        validarCategoriaNoRegistrada(usuario.getId(), alimentacion, "Alimentación");
        validarCategoriaNoRegistrada(usuario.getId(), residuos, "Residuos");
        guardarHabito(usuario, alimentacion, "tipo", BigDecimal.ZERO, dto.getTipo());
        guardarHabito(usuario, residuos, "plasticos", BigDecimal.ZERO, dto.getPlasticos());
        guardarHabito(usuario, residuos, "reciclas",
                dto.getReciclas() ? BigDecimal.ONE : BigDecimal.ZERO, "boolean");

        return new AlimentacionResiduosRespuestaDTO(
                usuario.getId(),
                alimentacion.getId(),
                residuos.getId(),
                dto.getTipo(),
                dto.getPlasticos(),
                dto.getReciclas()
        );
    }

    @Override
    @Transactional
    public HabitoRespuestaDTO actualizar(Long id, Usuario usuario, ActualizarHabitoDTO dto) {
        if (dto == null || dto.getNombre() == null || dto.getNombre().isBlank()
                || dto.getValor() == null || dto.getUnidad() == null || dto.getUnidad().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nombre, valor y unidad son obligatorios");
        }

        Habito habito = habitoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Hábito no encontrado"));

        if (habito.getUsuario() == null || !habito.getUsuario().getId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tiene autorización para modificar este hábito");
        }

        validarCompatibilidad(habito, dto);
        habito.setValor(dto.getValor());
        habito.setUnidad(dto.getUnidad().trim());
        habito.setFechaActualizacion(LocalDateTime.now());

        return convertirADTO(habitoRepository.save(habito));
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
            String nombreNormalizado = normalizarCategoria(nombre);
            categoria = categoriaHabitoRepository.findAll().stream()
                    .filter(categoriaExistente -> categoriaExistente.getNombre() != null
                            && normalizarCategoria(categoriaExistente.getNombre()).equals(nombreNormalizado))
                    .findFirst()
                    .orElse(null);
        }
        if (categoria == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría " + nombre + " no encontrada");
        }
        return categoria;
    }

    private void validarCategoriaNoRegistrada(Long usuarioId, CategoriaHabito categoria, String nombreCategoria) {
        if (habitoRepository.countByUsuario_IdAndCategoria_NombreIgnoreCase(
                usuarioId, categoria.getNombre()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría " + nombreCategoria
                            + " ya está registrada; utilice PUT /habitos/{id} para actualizarla");
        }
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
        return convertirADTO(guardado);
    }

    private HabitoRespuestaDTO convertirADTO(Habito habito) {
        return new HabitoRespuestaDTO(
                habito.getId(), habito.getUsuario().getId(), habito.getCategoria().getId(),
                habito.getNombre(), habito.getValor(), habito.getUnidad(),
                habito.getFechaRegistro(), habito.getFechaActualizacion()
        );
    }

    private void validarCompatibilidad(Habito habito, ActualizarHabitoDTO dto) {
        String categoria = normalizarCategoria(habito.getCategoria().getNombre());
        String nombre = dto.getNombre().trim().toLowerCase(Locale.ROOT);
        String nombreActual = habito.getNombre() == null ? "" : habito.getNombre().toLowerCase(Locale.ROOT);
        BigDecimal valor = dto.getValor();
        String unidad = dto.getUnidad().trim().toLowerCase(Locale.ROOT);

        Set<String> nombresPermitidos = switch (categoria) {
            case "transporte" -> Set.of("medio", "kmsemana", "diassemana");
            case "energia" -> Set.of("vivienda", "personas", "fuente");
            case "alimentacion" -> Set.of("tipo");
            case "residuos" -> Set.of("plasticos", "reciclas");
            default -> Set.of();
        };

        if (!nombresPermitidos.contains(nombre) || !nombre.equals(nombreActual)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre debe coincidir con el hábito registrado en su categoría");
        }

        boolean entero = valor.stripTrailingZeros().scale() <= 0;
        if ("diassemana".equals(nombre)
                && (!entero || valor.compareTo(BigDecimal.ONE) < 0
                || valor.compareTo(BigDecimal.valueOf(7)) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "diasSemana debe ser un número entero entre 1 y 7");
        }
        boolean valido = switch (nombre) {
            case "medio" -> valor.signum() == 0
                    && Set.of("bicicleta", "caminando", "bus", "auto", "moto").contains(unidad);
            case "vivienda" -> valor.signum() == 0
                    && Set.of("casa", "departamento").contains(unidad);
            case "fuente" -> valor.signum() == 0
                    && Set.of("electricidad", "gas", "solar").contains(unidad);
            case "tipo" -> valor.signum() == 0
                    && Set.of("vegana", "vegetariana", "mixta").contains(unidad);
            case "plasticos" -> valor.signum() == 0
                    && Set.of("bajo", "medio", "alto").contains(unidad);
            case "kmsemana" -> valor.signum() > 0 && "km/semana".equals(unidad);
            case "diassemana" -> entero && valor.signum() > 0
                    && valor.compareTo(BigDecimal.valueOf(7)) <= 0 && "dias/semana".equals(unidad);
            case "personas" -> entero && valor.signum() > 0 && "personas".equals(unidad);
            case "reciclas" -> entero && (valor.signum() == 0 || valor.compareTo(BigDecimal.ONE) == 0)
                    && "boolean".equals(unidad);
            default -> false;
        };
        if (!valido) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Valor o unidad inválidos para " + habito.getNombre());
        }
    }

    private String normalizarCategoria(String nombre) {
        return Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
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
