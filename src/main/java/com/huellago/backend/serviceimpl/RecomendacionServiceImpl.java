package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.dtos.ConversionRecomendacionDTO;
import com.huellago.backend.dtos.RecomendacionPersonalizadaDTO;
import com.huellago.backend.dtos.RecomendacionCompletadaDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.services.RecomendacionService;
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

    @Override
    @Transactional
    public RecomendacionCompletadaDTO completar(Integer asociacionId, Usuario autenticado) {
        Long usuarioId = identidad(autenticado);
        if (asociacionId == null || asociacionId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El identificador de la recomendación debe ser positivo");
        }
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

    private Long identidad(Usuario autenticado) {
        if (autenticado == null || autenticado.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");
        }
        return autenticado.getId();
    }

    private RecomendacionCompletadaDTO completadaDTO(UsuarioRecomendacionIA asociacion) {
        RecomendacionIA recomendacion = asociacion.getRecomendacionIA();
        return new RecomendacionCompletadaDTO(asociacion.getId(),
                recomendacion == null ? null : recomendacion.getId(),
                recomendacion == null ? null : recomendacion.getTitulo(),
                recomendacion == null ? null : recomendacion.getDescripcion(),
                asociacion.getEstado(), asociacion.getFechaGeneracion(), asociacion.getFechaCompletado());
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
        RecomendacionIA recomendacion = asociacion.getRecomendacionIA();
        if (recomendacion == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La recomendación no tiene contenido para convertir");
        }

        Reto reto = new Reto();
        reto.setTitulo(recomendacion.getTitulo());
        reto.setDescripcion(recomendacion.getDescripcion());
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
        List<RecomendacionPersonalizadaDTO> recomendaciones = new ArrayList<>();
        for (Accion accion : acciones) {
            RecomendacionIA recomendacion = obtenerCatalogo(accion);
            UsuarioRecomendacionIA asociacion = usuarioRecomendacionIARepository
                    .findFirstByUsuario_IdAndRecomendacionIA_IdOrderByIdAsc(
                            usuarioId, recomendacion.getId())
                    .orElseGet(() -> asociar(usuario, recomendacion));
            BigDecimal emision = emisiones.get(accion.categoria());
            String prioridad = emision != null && emision.signum() > 0
                    && emision.compareTo(mayorEmision) == 0 ? "ALTA" : "MEDIA";
            recomendaciones.add(new RecomendacionPersonalizadaDTO(
                    asociacion.getId(), recomendacion.getId(), accion.categoria().nombre,
                    recomendacion.getTitulo(), recomendacion.getDescripcion(), accion.motivo(),
                    prioridad, emision, asociacion.getEstado(), asociacion.getFechaGeneracion()));
        }

        String estado = acciones.isEmpty() ? "DATOS_INSUFICIENTES"
                : faltantes.isEmpty() ? "GENERADAS" : "PARCIALES";
        String mensaje = switch (estado) {
            case "DATOS_INSUFICIENTES" -> "Completa los datos indicados para recibir recomendaciones confiables";
            case "PARCIALES" -> "Recomendaciones basadas en las categorías completas; revisa los datos faltantes";
            default -> "Recomendaciones personalizadas mediante reglas sobre tus hábitos registrados";
        };
        return new GeneracionRecomendacionesDTO(usuarioId, "REGLAS_DETERMINISTAS", estado,
                mensaje, usarHuella ? "HUELLA_REGISTRADA" : "HABITOS_REGISTRADOS",
                usarHuella ? huella.getId() : null, usarHuella ? huella.getFechaCalculo() : null,
                usarHuella ? "kg CO2e" : null, usarHuella ? "semanal" : null,
                faltantes, advertencias, recomendaciones);
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
                    if (categoria.campos.contains(campo)) {
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

    private UsuarioRecomendacionIA asociar(Usuario usuario, RecomendacionIA recomendacion) {
        UsuarioRecomendacionIA nueva = new UsuarioRecomendacionIA();
        nueva.setUsuario(usuario);
        nueva.setRecomendacionIA(recomendacion);
        nueva.setEstado("Pendiente");
        nueva.setFechaGeneracion(LocalDateTime.now());
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
