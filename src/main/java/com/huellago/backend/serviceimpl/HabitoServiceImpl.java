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
import com.huellago.backend.events.InformacionAmbientalActualizada;
import org.springframework.context.ApplicationEventPublisher;
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
    ApplicationEventPublisher eventos;

    @Autowired
    HabitoRepository habitoRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    CategoriaHabitoRepository categoriaHabitoRepository;

    @Override
    @Transactional(readOnly = true)
    public java.util.List<HabitoRespuestaDTO> listar(Usuario usuario) {
        if (usuario == null || usuario.getId() == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida");
        return habitoRepository.findByUsuario_IdOrderByIdAsc(usuario.getId()).stream()
                .map(this::convertirADTO).toList();
    }

    @Override
    @Transactional
    public TransporteRespuestaDTO registrarTransporte(TransporteDTO dto) {
        validarNoNulo(dto, "solicitud");
        validarNoNulo(dto.getUsuarioId(), "usuarioId");
        validarPermitido(dto.getMedio(), Set.of("Bicicleta", "Caminando", "Bus", "Auto", "Moto"), "medio");
        validarPositivo(dto.getKmSemana(), "kmSemana");
        validarPositivo(dto.getDiasSemana(), "diasSemana");
        if (dto.getDiasSemana() > 7) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "diasSemana debe ser un número entero entre 1 y 7");

        Usuario usuario = buscarUsuario(dto.getUsuarioId());
        CategoriaHabito categoria = buscarCategoria("Transporte");
        validarCategoriaNoRegistrada(usuario.getId(), categoria, "Transporte");
        guardarHabito(usuario, categoria, "medio", BigDecimal.ZERO, dto.getMedio());
        guardarHabito(usuario, categoria, "kmSemana", dto.getKmSemana(), "km/semana");
        guardarHabito(usuario, categoria, "diasSemana", BigDecimal.valueOf(dto.getDiasSemana()), "dias/semana");
        eventos.publishEvent(new InformacionAmbientalActualizada(usuario.getId()));

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
        validarNoNulo(dto, "solicitud");
        validarNoNulo(dto.getUsuarioId(), "usuarioId");
        validarPermitido(dto.getVivienda(), Set.of("Casa", "Departamento"), "vivienda");
        validarPositivo(dto.getPersonas(), "personas");
        validarPermitido(dto.getFuente(), Set.of("Electricidad", "Gas", "Solar"), "fuente");
        if (dto.getConsumoKwhDiario() != null && (dto.getConsumoKwhDiario().signum() < 0
                || !"Electricidad".equalsIgnoreCase(dto.getFuente())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "consumoKwhDiario debe ser no negativo y requiere Electricidad");

        Usuario usuario = buscarUsuario(dto.getUsuarioId());
        CategoriaHabito categoria = buscarCategoria("Energía");
        validarCategoriaNoRegistrada(usuario.getId(), categoria, "Energía");
        guardarHabito(usuario, categoria, "vivienda", BigDecimal.ZERO, dto.getVivienda());
        guardarHabito(usuario, categoria, "personas", BigDecimal.valueOf(dto.getPersonas()), "personas");
        guardarHabito(usuario, categoria, "fuente", BigDecimal.ZERO, dto.getFuente());
        if (dto.getConsumoKwhDiario() != null)
            guardarHabito(usuario, categoria, "consumoKwhDiario", dto.getConsumoKwhDiario(), "kWh/dia");
        eventos.publishEvent(new InformacionAmbientalActualizada(usuario.getId()));

        EnergiaRespuestaDTO respuesta = new EnergiaRespuestaDTO(
                usuario.getId(),
                categoria.getId(),
                dto.getVivienda(),
                dto.getPersonas(),
                dto.getFuente()
        );
        respuesta.setConsumoKwhDiario(dto.getConsumoKwhDiario());
        return respuesta;
    }

    @Override
    @Transactional
    public AlimentacionResiduosRespuestaDTO registrarAlimentacionResiduos(AlimentacionResiduosDTO dto) {
        validarNoNulo(dto, "solicitud");
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
        eventos.publishEvent(new InformacionAmbientalActualizada(usuario.getId()));

        AlimentacionResiduosRespuestaDTO respuesta = new AlimentacionResiduosRespuestaDTO(
                usuario.getId(),
                alimentacion.getId(),
                residuos.getId(),
                dto.getTipo(),
                dto.getPlasticos(),
                dto.getReciclas()
        );
        var pendientes = ValidacionHabitosIniciales.categoriasPendientes(habitoRepository.findByUsuario_Id(usuario.getId()));
        respuesta.setCategoriasPendientes(pendientes);
        respuesta.setConfiguracionInicialCompleta(pendientes.isEmpty());
        return respuesta;
    }

    @Override
    @Transactional
    public HabitoRespuestaDTO actualizar(Long id, Usuario usuario, ActualizarHabitoDTO dto) {
        if (dto == null || dto.getNombre() == null || dto.getNombre().isBlank()
                || dto.getValor() == null || dto.getUnidad() == null || dto.getUnidad().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nombre, valor y unidad son obligatorios");
        }

        usuarioRepository.bloquearParaRecomendaciones(usuario.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"Usuario no encontrado"));
        Habito habito = habitoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Hábito no encontrado"));

        if (habito.getUsuario() == null || !habito.getUsuario().getId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tiene autorización para modificar este hábito");
        }

        validarCompatibilidad(habito, dto);
        if (habito.getValor() != null && habito.getValor().compareTo(dto.getValor()) == 0
                && habito.getUnidad() != null
                && habito.getUnidad().trim().equalsIgnoreCase(dto.getUnidad().trim())) {
            // Repetir los mismos datos no vuelve obsoleta una huella vigente.
            return convertirADTO(habito);
        }
        habito.setValor(dto.getValor());
        habito.setUnidad(dto.getUnidad().trim());
        habito.setFechaActualizacion(LocalDateTime.now());

        HabitoRespuestaDTO respuesta = convertirADTO(habitoRepository.save(habito));
        eventos.publishEvent(new InformacionAmbientalActualizada(usuario.getId()));
        return respuesta;
    }

    private Usuario buscarUsuario(Long usuarioId) {
        usuarioRepository.bloquearParaRecomendaciones(usuarioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"Usuario no encontrado"));
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
                    "Ya registraste tus hábitos de " + nombreCategoria.toLowerCase(Locale.ROOT)
                            + ". Puedes actualizar esta información cuando lo necesites.");
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
                habito.getId(), habito.getUsuario().getId(),
                habito.getCategoria() == null ? null : habito.getCategoria().getId(),
                habito.getNombre(), habito.getValor(), habito.getUnidad(),
                habito.getFechaRegistro(), habito.getFechaActualizacion(),
                habito.getCategoria() == null ? null : habito.getCategoria().getNombre()
        );
    }

    private void validarCompatibilidad(Habito habito, ActualizarHabitoDTO dto) {
        String categoria = normalizarCategoria(habito.getCategoria().getNombre());
        String nombre = dto.getNombre().trim().toLowerCase(Locale.ROOT);
        String nombreActual = habito.getNombre() == null ? "" : habito.getNombre().toLowerCase(Locale.ROOT);
        BigDecimal valor = dto.getValor();

        Set<String> nombresPermitidos = switch (categoria) {
            case "transporte" -> Set.of("medio", "kmsemana", "diassemana");
            case "energia" -> Set.of("vivienda", "personas", "fuente", "consumokwhdiario");
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
        Habito candidato = new Habito();
        candidato.setNombre(habito.getNombre()); candidato.setValor(valor); candidato.setUnidad(dto.getUnidad());
        String campo = switch (nombre) {
            case "kmsemana" -> "kmSemana";
            case "diassemana" -> "diasSemana";
            case "consumokwhdiario" -> "consumoKwhDiario";
            default -> nombre;
        };
        ValidacionHabitosIniciales.validarCampo(java.util.List.of(candidato), campo);
        if ("energia".equals(categoria)) {
            var datos = new java.util.ArrayList<>(habitoRepository.findByUsuario_Id(habito.getUsuario().getId()));
            if (datos.stream().anyMatch(h -> "consumoKwhDiario".equalsIgnoreCase(h.getNombre()))) {
                datos.removeIf(h -> h.getId() != null && h.getId().equals(habito.getId()));
                candidato.setCategoria(habito.getCategoria()); datos.add(candidato);
                if (ValidacionHabitosIniciales.categoriasPendientes(datos).contains("Energía"))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El consumo en kWh/dia requiere energía completa y fuente Electricidad");
            }
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
        if ("usuarioId".equals(campo) && valor instanceof Long id && id <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"usuarioId debe ser positivo");
        if (valor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo " + campo + " es obligatorio");
        }
    }

    private void validarPositivo(Number valor, String campo) {
        validarNoNulo(valor, campo);
        if (valor instanceof BigDecimal decimal ? decimal.signum() <= 0 : valor.longValue() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo " + campo + " debe ser positivo");
        }
    }
}
