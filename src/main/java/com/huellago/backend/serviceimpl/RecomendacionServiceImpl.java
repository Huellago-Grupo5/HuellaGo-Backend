package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.dtos.ConversionRecomendacionDTO;
import com.huellago.backend.dtos.RecomendacionPersonalizadaDTO;
import com.huellago.backend.dtos.RecomendacionCompletadaDTO;
import com.huellago.backend.dtos.EscenarioImpactoIADTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.services.RecomendacionService;
import com.huellago.backend.services.NotificacionService;
import com.huellago.backend.services.ContextoRecomendacionesIAService;
import com.huellago.backend.services.CatalogoAccionesAmbientalesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;

@Service
public class RecomendacionServiceImpl implements RecomendacionService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RecomendacionServiceImpl.class);

    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    HabitoRepository habitoRepository;
    @Autowired
    HuellaCarbonoRepository huellaCarbonoRepository;
    @Autowired
    CategoriaHabitoRepository categoriaHabitoRepository;
    @Autowired
    RecomendacionIARepository recomendacionIARepository;
    @Autowired
    UsuarioRecomendacionIARepository usuarioRecomendacionIARepository;
    @Autowired
    RetoRepository retoRepository;
    @Autowired
    UsuarioRetoRepository usuarioRetoRepository;
    @Autowired
    NotificacionService notificacionService;
    @Autowired
    ContextoRecomendacionesIAService contextoRecomendacionesIAService;
    @Autowired
    CatalogoAccionesAmbientalesService catalogoAccionesAmbientalesService;
    @Autowired
    PoliticaMedicionInicial politicaMedicionInicial;
    @Autowired(required = false)
    PresentacionesLocalizadasIA traducciones;

    @Override
    @Transactional
    public RecomendacionCompletadaDTO completar(Integer asociacionId, Usuario autenticado) {
        Long usuarioId = identidad(autenticado);
        if (asociacionId == null || asociacionId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El identificador de la recomendación debe ser positivo");
        }
        usuarioRepository.bloquearParaRecomendaciones(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        usuarioRecomendacionIARepository.bloquearPropia(asociacionId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Recomendación no encontrada para el usuario"));
        UsuarioRecomendacionIA asociacion = usuarioRecomendacionIARepository
                .findByIdAndUsuario_Id(asociacionId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Recomendación no encontrada para el usuario"));
        if ("completado".equalsIgnoreCase(asociacion.getEstado())
                || asociacion.getFechaCompletado() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La recomendación ya fue completada");
        }
        if (!"pendiente".equalsIgnoreCase(asociacion.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El estado de la recomendación no permite completarla");
        }
        // Comprobar antes de mutar o guardar: una instantánea parcial no es contenido heredado.
        contenidoAsociado(asociacion);
        asociacion.setEstado("completado");
        asociacion.setFechaCompletado(LocalDateTime.now());
        return completadaDTO(usuarioRecomendacionIARepository.saveAndFlush(asociacion));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecomendacionCompletadaDTO> historialCompletadas(Usuario autenticado) {
        return usuarioRecomendacionIARepository
                .findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(
                        identidad(autenticado), "completado")
                .stream().map(this::completadaDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecomendacionPersonalizadaDTO> pendientes(Usuario autenticado, String categoria) {
        return pendientes(autenticado, categoria, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecomendacionPersonalizadaDTO> pendientes(Usuario autenticado, String categoria,
            EscenarioImpactoIADTO escenario) {
        Long usuarioId = identidad(autenticado);
        String filtro = categoria == null ? null : normalizar(categoria);
        if (filtro != null && !Set.of("transporte", "energia", "alimentacion", "residuos").contains(filtro))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría ambiental no válida");
        var contexto = escenario == null || !escenario.presente() ? null
                : contextoRecomendacionesIAService.construir(usuarioId).contexto();
        if (contexto != null) ImpactoHipoteticoIA.validar(escenario, contexto);
        Set<String> compatibles = contexto == null ? Set.of()
                : catalogoAccionesAmbientalesService.compatibles(contexto, Set.of()).stream()
                    .map(c -> c.definicion().id() + "/" + c.definicion().version())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return usuarioRecomendacionIARepository
                .findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(usuarioId, "pendiente")
                .stream()
                .filter(a -> a != null && "pendiente".equalsIgnoreCase(a.getEstado())
                        && a.getUsuario() != null && usuarioId.equals(a.getUsuario().getId()))
                .filter(a -> filtro == null || a.getRecomendacionIA() != null
                        && a.getRecomendacionIA().getCategoria() != null
                        && filtro.equals(normalizar(a.getRecomendacionIA().getCategoria().getNombre())))
                .map(a -> {
                    var dto = pendienteDTO(a);
                    var canonica = a.getRecomendacionIA();
                    if (contexto != null && canonica != null && canonica.getAccionId() != null
                            && compatibles.contains(canonica.getAccionId() + "/" + canonica.getVersionAccion())
                            && "OLLAMA_SELECCION_PRESENTACION_CONTROLADA".equals(a.getMetodoGeneracion()))
                        ImpactoHipoteticoIA.aplicar(dto, canonica.getAccionId(), escenario, politicaMedicionInicial);
                    return dto;
                })
                // La prioridad cualitativa prevalece; la cifra hipotética solo desempata en la misma categoría.
                .sorted(Comparator.comparingInt((RecomendacionPersonalizadaDTO d) -> ordenPrioridad(d.getPrioridad()))
                        .thenComparingInt(d -> ordenCategoria(d.getCategoria()))
                        .thenComparing(RecomendacionPersonalizadaDTO::getImpactoPotencialKgCo2eSemanal,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RecomendacionPersonalizadaDTO::getFechaGeneracion,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RecomendacionPersonalizadaDTO::getUsuarioRecomendacionId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private RecomendacionPersonalizadaDTO pendienteDTO(UsuarioRecomendacionIA asociacion) {
        RecomendacionIA recomendacion = asociacion.getRecomendacionIA();
        if (recomendacion == null || recomendacion.getCategoria() == null)
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo recuperar la categoría de la recomendación");
        ContenidoAsociado contenido = contenidoAsociado(asociacion);
        String prioridad = asociacion.getPrioridadInstantanea() != null
                ? asociacion.getPrioridadInstantanea() : recomendacion.getPrioridad();
        var dto = new RecomendacionPersonalizadaDTO(asociacion.getId(), recomendacion.getId(),
                recomendacion.getCategoria().getNombre(), contenido.titulo(), contenido.descripcion(),
                asociacion.getMotivoInstantanea(), prioridad, null, asociacion.getEstado(),
                asociacion.getFechaGeneracion());
        if (recomendacion.getAccionId() != null && recomendacion.getVersionAccion() != null
                && "OLLAMA_SELECCION_PRESENTACION_CONTROLADA".equals(asociacion.getMetodoGeneracion())) {
            dto.setAccionId(recomendacion.getAccionId());
            dto.setVersionAccion(recomendacion.getVersionAccion());
            dto.indicarImpactoNoEstimable();
            if (PrioridadAccionAmbientalIA.BASE.equals(asociacion.getBasePriorizacion()))
                dto.setJustificacionPrioridad(PrioridadAccionAmbientalIA.evaluar(
                        recomendacion.getAccionId(), recomendacion.getVersionAccion()).justificacion());
        }
        if (traducciones != null) traducciones.localizar(asociacion).ifPresent(texto -> {
            dto.setTitulo(texto.titulo()); dto.setDescripcion(texto.descripcion()); dto.setMotivo(texto.motivo());
        });
        return dto;
    }

    private int ordenPrioridad(String prioridad) {
        if (prioridad == null) return 3;
        return switch (prioridad.toUpperCase(Locale.ROOT)) {
            case "ALTA" -> 0; case "MEDIA" -> 1; case "BAJA" -> 2; default -> 3;
        };
    }

    private int ordenCategoria(String categoria) {
        return switch (normalizar(categoria)) {
            case "transporte" -> 0; case "energia" -> 1; case "alimentacion" -> 2;
            case "residuos" -> 3; default -> 4;
        };
    }

    private Long identidad(Usuario autenticado) {
        if (autenticado == null || autenticado.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");
        }
        return autenticado.getId();
    }

    private RecomendacionCompletadaDTO completadaDTO(UsuarioRecomendacionIA asociacion) {
        RecomendacionIA recomendacion = asociacion.getRecomendacionIA();
        ContenidoAsociado contenido = contenidoAsociado(asociacion);
        if (traducciones != null) {
            var traducido = traducciones.localizar(asociacion);
            if (traducido.isPresent()) contenido = new ContenidoAsociado(traducido.get().titulo(), traducido.get().descripcion());
        }
        return new RecomendacionCompletadaDTO(asociacion.getId(),
                recomendacion == null ? null : recomendacion.getId(),
                contenido.titulo(), contenido.descripcion(),
                asociacion.getEstado(), asociacion.getFechaGeneracion(), asociacion.getFechaCompletado());
    }

    private record ContenidoAsociado(String titulo, String descripcion) { }

    private ContenidoAsociado contenidoAsociado(UsuarioRecomendacionIA asociacion) {
        RecomendacionIA compartida = asociacion.getRecomendacionIA();
        // Prioridad, base y huella de origen también las escribe el generador determinista
        // sin instantáneas de texto. No son una identidad editorial ni contenido personalizado.
        // Una identidad IA o cualquier parte de la instantánea sí exige los tres textos completos.
        boolean personalizada = compartida != null
                && (compartida.getAccionId() != null || compartida.getVersionAccion() != null);
        personalizada = personalizada || java.util.stream.Stream.of(
                asociacion.getTituloInstantanea(), asociacion.getDescripcionInstantanea(),
                asociacion.getMotivoInstantanea(), asociacion.getEnfoque(),
                asociacion.getVersionPresentacion(), asociacion.getMetodoGeneracion(),
                asociacion.getReferenciaContexto(), asociacion.getModeloGeneracion()).anyMatch(Objects::nonNull);
        if (!personalizada) return new ContenidoAsociado(
                compartida == null ? null : compartida.getTitulo(),
                compartida == null ? null : compartida.getDescripcion());
        if (asociacion.getTituloInstantanea() == null || asociacion.getTituloInstantanea().isBlank()
                || asociacion.getDescripcionInstantanea() == null || asociacion.getDescripcionInstantanea().isBlank()
                || asociacion.getMotivoInstantanea() == null || asociacion.getMotivoInstantanea().isBlank()) {
            LOG.warn("Instantánea de recomendación incompleta: asociacionId={}, accionIdPresente={}, "
                            + "tituloPresente={}, descripcionPresente={}, motivoPresente={}, metodoPresente={}",
                    asociacion.getId(), compartida != null && compartida.getAccionId() != null,
                    asociacion.getTituloInstantanea() != null && !asociacion.getTituloInstantanea().isBlank(),
                    asociacion.getDescripcionInstantanea() != null && !asociacion.getDescripcionInstantanea().isBlank(),
                    asociacion.getMotivoInstantanea() != null && !asociacion.getMotivoInstantanea().isBlank(),
                    asociacion.getMetodoGeneracion() != null);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo recuperar el contenido personalizado de la recomendación");
        }
        // Conservar los textos exactos, sin trim, reconstrucción ni modificación de la entidad.
        return new ContenidoAsociado(asociacion.getTituloInstantanea(), asociacion.getDescripcionInstantanea());
    }

    @Override
    @Transactional
    public ConversionRecomendacionDTO convertirEnReto(Integer asociacionId, Usuario autenticado) {
        if (autenticado == null || autenticado.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");
        }
        Long usuarioId = autenticado.getId();
        usuarioRecomendacionIARepository.bloquearPropia(asociacionId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Recomendación no encontrada para el usuario"));
        UsuarioRecomendacionIA asociacion = usuarioRecomendacionIARepository.findById(asociacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Recomendación no encontrada para el usuario"));
        if (retoRepository.existsByRecomendacionOrigen_Id(asociacionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La recomendación ya fue convertida en reto");
        }
        ContenidoAsociado contenido = contenidoAsociado(asociacion);
        RecomendacionIA recomendacion = asociacion.getRecomendacionIA();
        if (recomendacion == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La recomendación no tiene contenido para convertir");
        }

        Reto reto = new Reto();
        reto.setTitulo(contenido.titulo());
        reto.setDescripcion(contenido.descripcion());
        reto.setCategoria(recomendacion.getCategoria());
        reto.setPuntosRecompensa(0);
        reto.setFechaInicio(LocalDate.now());
        reto.setActivo(true);
        reto.setTipo(TipoReto.INDIVIDUAL);
        reto.setRecomendacionOrigen(asociacion);
        reto = retoRepository.saveAndFlush(reto);

        UsuarioReto participacion = new UsuarioReto();
        participacion.setUsuario(asociacion.getUsuario());
        participacion.setReto(reto);
        participacion.setEstado("activo");
        participacion.setProgreso(BigDecimal.ZERO);
        participacion.setFechaAceptacion(LocalDateTime.now());
        participacion = usuarioRetoRepository.saveAndFlush(participacion);
        return new ConversionRecomendacionDTO("Recomendación convertida en reto personal",
                asociacionId, reto.getId(), participacion.getId(), participacion.getEstado(),
                participacion.getFechaAceptacion());
    }

    private enum Categoria {
        TRANSPORTE("Transporte", "medio", "kmsemana", "diassemana"),
        ENERGIA("Energía", "vivienda", "personas", "fuente"),
        ALIMENTACION("Alimentación", "tipo"),
        RESIDUOS("Residuos", "plasticos", "reciclas");

        final String nombre;
        final List<String> campos;

        Categoria(String nombre, String... campos) {
            this.nombre = nombre;
            this.campos = List.of(campos);
        }
    }

    private record Accion(Categoria categoria, CategoriaHabito entidadCategoria,
                          String titulo, String descripcion, String motivo) { }

    @Override
    @Transactional
    public GeneracionRecomendacionesDTO generar(Usuario usuarioAutenticado) {
        if (usuarioAutenticado == null || usuarioAutenticado.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");
        }
        Long usuarioId = usuarioAutenticado.getId();
        usuarioRepository.bloquearParaRecomendaciones(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no encontrado"));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no encontrado"));

        Map<Categoria, Map<String, Habito>> datos = seleccionarHabitos(
                habitoRepository.findByUsuario_Id(usuarioId));
        List<String> faltantes = new ArrayList<>();
        List<Accion> acciones = new ArrayList<>();
        for (Categoria categoria : Categoria.values()) {
            Map<String, Habito> campos = datos.get(categoria);
            int erroresAnteriores = faltantes.size();
            for (String campo : categoria.campos) {
                if (!esValido(campo, campos.get(campo))) {
                    faltantes.add(categoria.nombre + "." + nombreCampo(campo)
                            + ": " + requisito(campo));
                }
            }
            if (faltantes.size() == erroresAnteriores) {
                acciones.add(personalizar(categoria, campos));
            }
        }

        List<String> advertencias = new ArrayList<>();
        HuellaCarbono huella = huellaCarbonoRepository
                .findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(usuarioId);
        boolean usarHuella = faltantes.isEmpty() && huellaUtilizable(huella, usuarioId, datos);
        Map<Categoria, BigDecimal> emisiones = usarHuella ? emisiones(huella) : Map.of();
        if (!usarHuella) {
            advertencias.add("No se prioriza por emisiones: falta un desglose válido y actualizado "
                    + "para los hábitos actuales. Puedes completar los hábitos y solicitar el cálculo "
                    + "de huella por separado.");
        } else {
            acciones.sort(Comparator.comparing(
                    (Accion accion) -> emisiones.get(accion.categoria())).reversed());
        }

        // Orden de bloqueo estable para compartir el catálogo entre usuarios sin duplicarlo.
        acciones.stream().map(accion -> accion.entidadCategoria().getId()).distinct().sorted()
                .forEach(id -> categoriaHabitoRepository.bloquearParaRecomendaciones(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Categoría ambiental no encontrada")));

        BigDecimal mayorEmision = emisiones.values().stream()
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        Set<Integer> origenesRealizados = new HashSet<>();
        usuarioRetoRepository.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaAceptacionDesc(
                        usuarioId, "completado").stream()
                .filter(p -> p.getReto() != null && p.getReto().getRecomendacionOrigen() != null)
                .forEach(p -> origenesRealizados.add(p.getReto().getRecomendacionOrigen().getId()));
        List<RecomendacionPersonalizadaDTO> recomendaciones = new ArrayList<>();
        for (Accion original : acciones) {
            BigDecimal emision = emisiones.get(original.categoria());
            boolean mayorImpacto = emision != null && emision.signum() > 0
                    && emision.compareTo(mayorEmision) == 0;
            Accion accion = mayorImpacto ? enfocarMayorImpacto(original) : original;
            RecomendacionIA recomendacion = obtenerCatalogo(accion);
            var existente = usuarioRecomendacionIARepository
                    .findFirstByUsuario_IdAndRecomendacionIA_IdOrderByIdAsc(usuarioId, recomendacion.getId());
            String prioridad = mayorImpacto ? "ALTA" : "MEDIA";
            UsuarioRecomendacionIA asociacion = existente.orElseGet(() -> asociar(usuario, recomendacion,
                    prioridad, usarHuella ? "HUELLA_REGISTRADA" : "HABITOS_REGISTRADOS"));
            if (asociacion.getFechaCompletado() != null
                    || "completado".equalsIgnoreCase(asociacion.getEstado())
                    || origenesRealizados.contains(asociacion.getId())) {
                continue;
            }
            if (existente.isEmpty() && "ALTA".equals(prioridad))
                notificacionService.crearPorEvento(usuario, "RECOMENDACION_ALTA:"+asociacion.getId(),
                        "Nueva recomendación prioritaria", "Tienes una nueva recomendación prioritaria de "
                                + accion.categoria().nombre+".", "RECOMENDACION_ALTA");
            recomendaciones.add(new RecomendacionPersonalizadaDTO(
                    asociacion.getId(), recomendacion.getId(), accion.categoria().nombre,
                    recomendacion.getTitulo(), recomendacion.getDescripcion(), accion.motivo(),
                    prioridad, emision, asociacion.getEstado(), asociacion.getFechaGeneracion()));
        }

        String estado = acciones.isEmpty() ? "DATOS_INSUFICIENTES"
                : recomendaciones.isEmpty() ? "SIN_SUGERENCIAS_NUEVAS"
                : faltantes.isEmpty() ? "GENERADAS" : "PARCIALES";
        String mensaje = switch (estado) {
            case "DATOS_INSUFICIENTES" -> "Completa los datos indicados para recibir recomendaciones confiables";
            case "PARCIALES" -> "Recomendaciones basadas en las categorías completas; revisa los datos faltantes";
            case "SIN_SUGERENCIAS_NUEVAS" -> "Las acciones recomendadas ya se realizaron; se conserva tu historial";
            default -> "Recomendaciones personalizadas mediante reglas sobre tus hábitos registrados";
        };
        return new GeneracionRecomendacionesDTO(usuarioId, "REGLAS_DETERMINISTAS", estado,
                mensaje, usarHuella ? "HUELLA_REGISTRADA" : "HABITOS_REGISTRADOS",
                usarHuella ? huella.getId() : null, usarHuella ? huella.getFechaCalculo() : null,
                usarHuella ? "kg CO2e" : null, usarHuella ? "semanal" : null,
                faltantes, advertencias, recomendaciones);
    }

    /** La categoría de mayor emisión positiva cambia la acción propuesta, no solo su orden.
     *  No se asignan porcentajes de ahorro ni se infieren cambios de hábitos a partir de retos. */
    private Accion enfocarMayorImpacto(Accion accion) {
        String titulo = switch (accion.categoria()) {
            case TRANSPORTE -> "Agrupa trayectos en tu principal fuente de emisiones";
            case ENERGIA -> "Identifica consumos evitables en tu principal fuente de emisiones";
            case ALIMENTACION -> "Planifica compras en tu principal fuente de emisiones";
            case RESIDUOS -> "Evita desechables en tu principal fuente de emisiones";
        };
        String paso = switch (accion.categoria()) {
            case TRANSPORTE -> "Antes de salir, agrupa los destinos cercanos y elimina desplazamientos evitables.";
            case ENERGIA -> "Haz una lista de equipos que quedan encendidos sin uso y acuerda cuándo apagarlos.";
            case ALIMENTACION -> "Revisa tus alimentos disponibles y prepara una lista de compras ajustada al menú.";
            case RESIDUOS -> "Identifica los envases desechables que más utilizas y elige una alternativa reutilizable.";
        };
        // El título incluye la acción base para conservar la personalización de cada hábito.
        return new Accion(accion.categoria(), accion.entidadCategoria(),
                titulo + ": " + accion.titulo(), accion.descripcion() + " " + paso,
                accion.motivo() + " Esta categoría tiene la mayor emisión positiva del último cálculo vigente.");
    }

    private Map<Categoria, Map<String, Habito>> seleccionarHabitos(List<Habito> habitos) {
        Map<Categoria, Map<String, Habito>> datos = new EnumMap<>(Categoria.class);
        for (Categoria categoria : Categoria.values()) {
            datos.put(categoria, new HashMap<>());
        }
        Comparator<Habito> orden = Comparator.comparing(this::fechaHabito,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Habito::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
        for (Habito habito : habitos) {
            if (habito.getCategoria() == null) {
                continue;
            }
            for (Categoria categoria : Categoria.values()) {
                if (normalizar(categoria.nombre).equals(normalizar(habito.getCategoria().getNombre()))) {
                    String campo = normalizar(habito.getNombre());
                    // El consumo opcional participa en la vigencia, no en los campos obligatorios.
                    if (categoria.campos.contains(campo)
                            || (categoria == Categoria.ENERGIA && "consumokwhdiario".equals(campo))) {
                        datos.get(categoria).merge(campo, habito,
                                (anterior, actual) -> orden.compare(actual, anterior) > 0 ? actual : anterior);
                    }
                }
            }
        }
        return datos;
    }

    private boolean esValido(String campo, Habito habito) {
        if (habito == null) {
            return false;
        }
        String unidad = normalizar(habito.getUnidad());
        Set<String> opciones = switch (campo) {
            case "medio" -> Set.of("auto", "moto", "bus", "bicicleta", "caminando");
            case "vivienda" -> Set.of("casa", "departamento");
            case "fuente" -> Set.of("electricidad", "gas", "solar");
            case "tipo" -> Set.of("mixta", "vegetariana", "vegana");
            case "plasticos" -> Set.of("bajo", "medio", "alto");
            default -> Set.of();
        };
        if (!opciones.isEmpty()) {
            return opciones.contains(unidad);
        }
        BigDecimal valor = habito.getValor();
        if (valor == null) {
            return false;
        }
        boolean entero = valor.stripTrailingZeros().scale() <= 0;
        return switch (campo) {
            case "kmsemana" -> "km/semana".equals(unidad) && valor.signum() > 0;
            case "diassemana" -> "dias/semana".equals(unidad) && entero
                    && valor.signum() > 0 && valor.compareTo(BigDecimal.valueOf(7)) <= 0;
            case "personas" -> "personas".equals(unidad) && entero && valor.signum() > 0;
            case "reciclas" -> "boolean".equals(unidad)
                    && (valor.compareTo(BigDecimal.ZERO) == 0 || valor.compareTo(BigDecimal.ONE) == 0);
            default -> false;
        };
    }

    private String requisito(String campo) {
        return switch (campo) {
            case "medio" -> "registra Auto, Moto, Bus, Bicicleta o Caminando";
            case "kmsemana" -> "registra kilómetros semanales positivos en km/semana";
            case "diassemana" -> "registra entre 1 y 7 días enteros en dias/semana";
            case "vivienda" -> "registra Casa o Departamento";
            case "personas" -> "registra un número entero positivo de personas";
            case "fuente" -> "registra Electricidad, Gas o Solar";
            case "tipo" -> "registra dieta Mixta, Vegetariana o Vegana";
            case "plasticos" -> "registra consumo Bajo, Medio o Alto";
            default -> "registra si reciclas, con valor 0 o 1 y unidad boolean";
        };
    }

    private String nombreCampo(String campo) {
        return switch (campo) {
            case "kmsemana" -> "kmSemana";
            case "diassemana" -> "diasSemana";
            default -> campo;
        };
    }

    private Accion personalizar(Categoria categoria, Map<String, Habito> campos) {
        CategoriaHabito entidad = campos.values().stream().map(Habito::getCategoria)
                .min(Comparator.comparing(CategoriaHabito::getId)).orElseThrow();
        String titulo;
        String descripcion;
        String motivo;
        switch (categoria) {
            case TRANSPORTE -> {
                String medio = texto(campos, "medio");
                motivo = "Registraste " + medio + ", " + numero(campos, "kmsemana")
                        + " km por semana y " + numero(campos, "diassemana") + " días de uso.";
                switch (normalizar(medio)) {
                    case "auto", "moto" -> {
                        titulo = "Alterna el " + normalizar(medio) + " con movilidad sostenible";
                        descripcion = "Identifica trayectos cortos que puedas hacer a pie o en bicicleta. "
                                + "Para los demás, evalúa transporte público y agrupa tus desplazamientos.";
                    }
                    case "bus" -> {
                        titulo = "Combina el bus con trayectos a pie";
                        descripcion = "Planifica rutas directas en bus y realiza a pie los tramos cortos "
                                + "cuando sea seguro, evitando sustituirlos por viajes individuales motorizados.";
                    }
                    case "bicicleta" -> {
                        titulo = "Mantén tus desplazamientos en bicicleta";
                        descripcion = "Organiza tus rutas habituales y revisa el estado de tu bicicleta "
                                + "para conservar este medio en los desplazamientos que ya realizas.";
                    }
                    default -> {
                        titulo = "Conserva tus desplazamientos a pie";
                        descripcion = "Organiza tus recados por zonas y conserva los recorridos a pie "
                                + "que ya realizas; para distancias largas, evalúa transporte público.";
                    }
                }
            }
            case ENERGIA -> {
                String fuente = texto(campos, "fuente");
                motivo = "Registraste " + texto(campos, "vivienda") + " con "
                        + numero(campos, "personas") + " personas y fuente " + fuente + ".";
                switch (normalizar(fuente)) {
                    case "electricidad" -> {
                        titulo = "Reduce consumos eléctricos evitables en casa";
                        descripcion = "Acuerda apagar luces en espacios vacíos y desconectar equipos "
                                + "que no se usen. Revisa la eficiencia de las bombillas al reemplazarlas.";
                    }
                    case "gas" -> {
                        titulo = "Organiza el uso de gas en el hogar";
                        descripcion = "Agrupa las preparaciones al cocinar y utiliza tapas y recipientes "
                                + "adecuados para aprovechar el calor. Apaga los equipos cuando termines de usarlos.";
                    }
                    default -> {
                        titulo = "Aprovecha tu fuente solar con consumo eficiente";
                        descripcion = "Si tu instalación lo permite, programa tareas de mayor consumo "
                                + "durante las horas de generación solar y evita dejar equipos encendidos sin uso.";
                    }
                }
            }
            case ALIMENTACION -> {
                String tipo = texto(campos, "tipo");
                motivo = "Registraste una alimentación " + tipo + ".";
                switch (normalizar(tipo)) {
                    case "mixta" -> {
                        titulo = "Incorpora comidas vegetales en tu dieta mixta";
                        descripcion = "Planifica comidas con legumbres y verduras para sustituir algunas "
                                + "preparaciones con carne. Compra según el menú para evitar desperdicios.";
                    }
                    case "vegetariana" -> {
                        titulo = "Planifica tu menú vegetariano sin desperdicios";
                        descripcion = "Organiza tus compras de legumbres y verduras de temporada según "
                                + "las porciones que consumirás y aprovecha los alimentos disponibles antes de comprar más.";
                    }
                    default -> {
                        titulo = "Conserva tu dieta vegana y aprovecha los alimentos";
                        descripcion = "Planifica tu menú vegetal con productos de temporada, almacena "
                                + "bien los alimentos y aprovecha los ingredientes que ya tienes antes de reponerlos.";
                    }
                }
            }
            default -> {
                String plasticos = texto(campos, "plasticos");
                boolean recicla = campos.get("reciclas").getValor().compareTo(BigDecimal.ONE) == 0;
                motivo = "Registraste uso de plásticos " + plasticos + " y "
                        + (recicla ? "que ya reciclas." : "que todavía no reciclas.");
                if (!recicla) {
                    titulo = "Comienza a separar tus residuos reciclables";
                    descripcion = "Separa los materiales limpios y secos de los residuos no reciclables "
                            + "y consulta los puntos de recogida de tu localidad antes de entregarlos.";
                } else if ("alto".equals(normalizar(plasticos))) {
                    titulo = "Reduce los plásticos aunque ya recicles";
                    descripcion = "Identifica envases desechables frecuentes y reemplázalos por "
                            + "recipientes reutilizables o recargas. Conserva la separación de lo que aún generes.";
                } else if ("medio".equals(normalizar(plasticos))) {
                    titulo = "Reutiliza antes de reciclar tus envases";
                    descripcion = "Lleva recipientes reutilizables a tus compras y prioriza productos "
                            + "con menos envase. Mantén limpios y separados los materiales reciclables.";
                } else {
                    titulo = "Mantén la separación y el bajo uso de plásticos";
                    descripcion = "Conserva tus recipientes reutilizables y revisa qué materiales "
                            + "acepta tu sistema local de reciclaje para evitar mezclas incorrectas.";
                }
            }
        }
        return new Accion(categoria, entidad, titulo, descripcion, motivo);
    }

    private RecomendacionIA obtenerCatalogo(Accion accion) {
        return recomendacionIARepository.findFirstByCategoria_IdAndTituloIgnoreCaseOrderByIdAsc(
                        accion.entidadCategoria().getId(), accion.titulo())
                .orElseGet(() -> {
                    RecomendacionIA nueva = new RecomendacionIA();
                    nueva.setCategoria(accion.entidadCategoria());
                    nueva.setTitulo(accion.titulo());
                    nueva.setDescripcion(accion.descripcion());
                    nueva.setPrioridad("MEDIA");
                    nueva.setPuntosRecompensa(0);
                    return recomendacionIARepository.save(nueva);
                });
    }

    private UsuarioRecomendacionIA asociar(Usuario usuario, RecomendacionIA recomendacion,
            String prioridad, String basePriorizacion) {
        UsuarioRecomendacionIA nueva = new UsuarioRecomendacionIA();
        nueva.setUsuario(usuario);
        nueva.setRecomendacionIA(recomendacion);
        nueva.setEstado("Pendiente");
        nueva.setFechaGeneracion(LocalDateTime.now());
        nueva.setPrioridadInstantanea(prioridad);
        nueva.setBasePriorizacion(basePriorizacion);
        return usuarioRecomendacionIARepository.save(nueva);
    }

    private boolean huellaUtilizable(HuellaCarbono huella, Long usuarioId,
                                     Map<Categoria, Map<String, Habito>> datos) {
        if (huella == null || huella.getFechaCalculo() == null
                || huella.getUsuario() == null || !usuarioId.equals(huella.getUsuario().getId())
                || huella.getCo2Total() == null || huella.getCo2Total().signum() < 0) {
            return false;
        }
        Collection<BigDecimal> valores = emisiones(huella).values();
        // Se comparan los componentes guardados; no se reconstruye el total redondeado.
        if (valores.stream().anyMatch(valor -> valor == null || valor.signum() < 0)) {
            return false;
        }
        return datos.values().stream().flatMap(campos -> campos.values().stream())
                .allMatch(habito -> fechaHabito(habito) != null
                        && !fechaHabito(habito).isAfter(huella.getFechaCalculo()));
    }

    private Map<Categoria, BigDecimal> emisiones(HuellaCarbono huella) {
        Map<Categoria, BigDecimal> valores = new EnumMap<>(Categoria.class);
        valores.put(Categoria.TRANSPORTE, huella.getCo2Transporte());
        valores.put(Categoria.ENERGIA, huella.getCo2Energia());
        valores.put(Categoria.ALIMENTACION, huella.getCo2Alimentacion());
        valores.put(Categoria.RESIDUOS, huella.getCo2Residuos());
        return valores;
    }

    private LocalDateTime fechaHabito(Habito habito) {
        if (habito.getFechaActualizacion() == null) {
            return habito.getFechaRegistro();
        }
        return habito.getFechaRegistro() != null
                && habito.getFechaRegistro().isAfter(habito.getFechaActualizacion())
                ? habito.getFechaRegistro() : habito.getFechaActualizacion();
    }

    private String texto(Map<String, Habito> campos, String campo) {
        return campos.get(campo).getUnidad().trim();
    }

    private String numero(Map<String, Habito> campos, String campo) {
        return campos.get(campo).getValor().stripTrailingZeros().toPlainString();
    }

    private String normalizar(String valor) {
        return valor == null ? "" : Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }
}
