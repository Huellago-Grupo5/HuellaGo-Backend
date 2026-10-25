package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.HuellaCarbonoRespuestaDTO;
import com.huellago.backend.dtos.ActividadHuellaDTO;
import com.huellago.backend.dtos.HuellaDesgloseDTO;
import com.huellago.backend.dtos.HuellaEquivalenciasDTO;
import com.huellago.backend.dtos.ComparacionPromedioDTO;
import com.huellago.backend.entities.HuellaCarbono;
import com.huellago.backend.entities.Habito;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import com.huellago.backend.repositories.UsuarioRepository;
import com.huellago.backend.services.HuellaCarbonoService;
import com.huellago.backend.events.InformacionAmbientalActualizada;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class HuellaCarbonoServiceImpl implements HuellaCarbonoService {

    @Autowired
    ApplicationEventPublisher eventos;

    private static final String UNIDAD_HUELLA = "kg CO2e";
    private static final String PERIODO_HUELLA = "semanal";

    private static final BigDecimal FACTOR_MEDIO_BICICLETA = new BigDecimal("0");
    private static final BigDecimal FACTOR_MEDIO_CAMINANDO = new BigDecimal("0");
    private static final BigDecimal FACTOR_MEDIO_BUS = new BigDecimal("0.05");
    private static final BigDecimal FACTOR_MEDIO_AUTO = new BigDecimal("0.12");
    private static final BigDecimal FACTOR_MEDIO_MOTO = new BigDecimal("0.08");

    private static final BigDecimal FACTOR_VIVIENDA_CASA = new BigDecimal("1.2");
    private static final BigDecimal FACTOR_VIVIENDA_DEPARTAMENTO = new BigDecimal("0.8");
    private static final BigDecimal FACTOR_FUENTE_ELECTRICIDAD = new BigDecimal("1");
    private static final BigDecimal FACTOR_FUENTE_GAS = new BigDecimal("0.85");
    private static final BigDecimal FACTOR_FUENTE_SOLAR = new BigDecimal("0.25");

    private static final BigDecimal FACTOR_TIPO_VEGANA = new BigDecimal("0.3");
    private static final BigDecimal FACTOR_TIPO_VEGETARIANA = new BigDecimal("0.5");
    private static final BigDecimal FACTOR_TIPO_MIXTA = new BigDecimal("1");

    private static final BigDecimal FACTOR_PLASTICOS_BAJO = new BigDecimal("0.2");
    private static final BigDecimal FACTOR_PLASTICOS_MEDIO = new BigDecimal("0.5");
    private static final BigDecimal FACTOR_PLASTICOS_ALTO = new BigDecimal("0.9");
    private static final BigDecimal FACTOR_RECICLA = new BigDecimal("0.7");
    private static final BigDecimal FACTOR_PERSONAS = new BigDecimal("0.25");

    @Autowired
    HuellaCarbonoRepository huellaCarbonoRepository;

    @Autowired
    HabitoRepository habitoRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public HuellaCarbonoRespuestaDTO calcularHuellaInicial(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        List<Habito> habitos = habitoRepository.findByUsuario_Id(usuarioId);
        Map<String, List<Habito>> habitosPorCategoria = habitos.stream()
                .filter(habito -> habito.getCategoria() != null
                        && habito.getCategoria().getNombre() != null)
                .collect(Collectors.groupingBy(
                        habito -> normalizarCategoria(habito.getCategoria().getNombre()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<String> categoriasFaltantes = List.of("Transporte", "Energía", "Alimentación", "Residuos")
                .stream()
                .filter(categoria -> !habitosPorCategoria.containsKey(normalizarCategoria(categoria)))
                .toList();
        if (!categoriasFaltantes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Faltan categorías por registrar: " + String.join(", ", categoriasFaltantes));
        }

        validarHabitos(habitosPorCategoria);

        BigDecimal co2Transporte = calcularTransporte(requerirCategoria(habitosPorCategoria, "transporte"));
        BigDecimal co2Energia = calcularEnergia(requerirCategoria(habitosPorCategoria, "energia"));
        BigDecimal co2Alimentacion = calcularAlimentacion(requerirCategoria(habitosPorCategoria, "alimentacion"));
        BigDecimal co2Residuos = calcularResiduos(requerirCategoria(habitosPorCategoria, "residuos"));
        BigDecimal co2Total = co2Transporte.add(co2Energia).add(co2Alimentacion).add(co2Residuos);

        HuellaCarbono huellaCarbono = new HuellaCarbono();
        huellaCarbono.setUsuario(usuario);
        huellaCarbono.setCo2Transporte(co2Transporte);
        huellaCarbono.setCo2Energia(co2Energia);
        huellaCarbono.setCo2Alimentacion(co2Alimentacion);
        huellaCarbono.setCo2Residuos(co2Residuos);
        huellaCarbono.setCo2Total(co2Total);
        huellaCarbono.setFechaCalculo(java.time.LocalDateTime.now());

        HuellaCarbonoRespuestaDTO respuesta = convertirADTO(huellaCarbonoRepository.save(huellaCarbono));
        eventos.publishEvent(new InformacionAmbientalActualizada(usuarioId));
        return respuesta;
    }

    @Override
    @Transactional
    public HuellaCarbonoRespuestaDTO recalcularHuella(Long usuarioId) {
        return calcularHuellaInicial(usuarioId);
    }

    private void validarHabitos(Map<String, List<Habito>> porCategoria) {
        validarCampo(porCategoria.get("transporte"), "medio");
        validarCampo(porCategoria.get("transporte"), "kmSemana");
        validarCampo(porCategoria.get("transporte"), "diasSemana");
        validarCampo(porCategoria.get("energia"), "vivienda");
        validarCampo(porCategoria.get("energia"), "personas");
        validarCampo(porCategoria.get("energia"), "fuente");
        validarCampo(porCategoria.get("alimentacion"), "tipo");
        validarCampo(porCategoria.get("residuos"), "plasticos");
        validarCampo(porCategoria.get("residuos"), "reciclas");
    }

    private void validarCampo(List<Habito> habitos, String nombre) {
        List<Habito> encontrados = habitos.stream()
                .filter(habito -> nombre.equalsIgnoreCase(habito.getNombre()))
                .toList();
        if (encontrados.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    encontrados.isEmpty() ? "Falta el hábito " + nombre
                            : "Hay registros duplicados del hábito " + nombre);
        }
        Habito habito = encontrados.get(0);
        BigDecimal valor = habito.getValor();
        String unidad = habito.getUnidad() == null ? ""
                : habito.getUnidad().trim().toLowerCase(Locale.ROOT);
        if (valor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Falta el valor del hábito " + nombre);
        }
        boolean entero = valor.stripTrailingZeros().scale() <= 0;
        boolean valido = switch (nombre) {
            case "medio" -> valor.signum() == 0
                    && Set.of("bicicleta", "caminando", "bus", "auto", "moto").contains(unidad);
            case "kmSemana" -> valor.signum() > 0 && "km/semana".equals(unidad);
            case "diasSemana" -> entero && valor.signum() > 0
                    && valor.compareTo(BigDecimal.valueOf(7)) <= 0 && "dias/semana".equals(unidad);
            case "vivienda" -> valor.signum() == 0
                    && Set.of("casa", "departamento").contains(unidad);
            case "personas" -> entero && valor.signum() > 0 && "personas".equals(unidad);
            case "fuente" -> valor.signum() == 0
                    && Set.of("electricidad", "gas", "solar").contains(unidad);
            case "tipo" -> valor.signum() == 0
                    && Set.of("vegana", "vegetariana", "mixta").contains(unidad);
            case "plasticos" -> valor.signum() == 0
                    && Set.of("bajo", "medio", "alto").contains(unidad);
            case "reciclas" -> entero
                    && (valor.signum() == 0 || valor.compareTo(BigDecimal.ONE) == 0)
                    && "boolean".equals(unidad);
            default -> false;
        };
        if (!valido) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Valor o unidad inválidos para " + nombre);
        }
    }

    private BigDecimal calcularTransporte(List<Habito> habitos) {
        String medio = valorTexto(requerirHabito(habitos, "medio"));
        BigDecimal kmSemana = valorNumerico(requerirHabito(habitos, "kmSemana"));
        BigDecimal factor = switch (medio.toLowerCase(Locale.ROOT)) {
            case "bicicleta" -> FACTOR_MEDIO_BICICLETA;
            case "caminando" -> FACTOR_MEDIO_CAMINANDO;
            case "bus" -> FACTOR_MEDIO_BUS;
            case "auto" -> FACTOR_MEDIO_AUTO;
            case "moto" -> FACTOR_MEDIO_MOTO;
            default -> throw datoInvalido("medio", medio);
        };
        return factor.multiply(kmSemana);
    }

    private BigDecimal calcularEnergia(List<Habito> habitos) {
        String vivienda = valorTexto(requerirHabito(habitos, "vivienda"));
        BigDecimal personas = valorNumerico(requerirHabito(habitos, "personas"));
        String fuente = valorTexto(requerirHabito(habitos, "fuente"));
        BigDecimal factorVivienda = switch (vivienda.toLowerCase(Locale.ROOT)) {
            case "casa" -> FACTOR_VIVIENDA_CASA;
            case "departamento" -> FACTOR_VIVIENDA_DEPARTAMENTO;
            default -> throw datoInvalido("vivienda", vivienda);
        };
        BigDecimal factorFuente = switch (fuente.toLowerCase(Locale.ROOT)) {
            case "electricidad" -> FACTOR_FUENTE_ELECTRICIDAD;
            case "gas" -> FACTOR_FUENTE_GAS;
            case "solar" -> FACTOR_FUENTE_SOLAR;
            default -> throw datoInvalido("fuente", fuente);
        };
        return factorVivienda.multiply(factorFuente)
                .multiply(BigDecimal.ONE.add(personas.subtract(BigDecimal.ONE).multiply(FACTOR_PERSONAS)));
    }

    private BigDecimal calcularAlimentacion(List<Habito> habitos) {
        String tipo = valorTexto(requerirHabito(habitos, "tipo"));
        BigDecimal factor = switch (tipo.toLowerCase(Locale.ROOT)) {
            case "vegana" -> FACTOR_TIPO_VEGANA;
            case "vegetariana" -> FACTOR_TIPO_VEGETARIANA;
            case "mixta" -> FACTOR_TIPO_MIXTA;
            default -> throw datoInvalido("tipo", tipo);
        };
        return factor.multiply(new BigDecimal("2"));
    }

    private BigDecimal calcularResiduos(List<Habito> habitos) {
        String plasticos = valorTexto(requerirHabito(habitos, "plasticos"));
        BigDecimal reciclas = valorNumerico(requerirHabito(habitos, "reciclas"));
        BigDecimal factor = switch (plasticos.toLowerCase(Locale.ROOT)) {
            case "bajo" -> FACTOR_PLASTICOS_BAJO;
            case "medio" -> FACTOR_PLASTICOS_MEDIO;
            case "alto" -> FACTOR_PLASTICOS_ALTO;
            default -> throw datoInvalido("plasticos", plasticos);
        };
        return factor.multiply(reciclas.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : FACTOR_RECICLA)
                .multiply(new BigDecimal("2"));
    }

    private List<Habito> requerirCategoria(Map<String, List<Habito>> habitos, String categoria) {
        List<Habito> encontrados = habitos.getOrDefault(categoria, List.of());
        if (encontrados.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan hábitos de categoría " + categoria);
        }
        return encontrados;
    }

    private Habito requerirHabito(List<Habito> habitos, String nombre) {
        return habitos.stream()
                .filter(habito -> nombre.equalsIgnoreCase(habito.getNombre()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Falta el hábito " + nombre));
    }

    private String valorTexto(Habito habito) {
        if (habito.getUnidad() == null || habito.getUnidad().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Falta el valor textual del hábito " + habito.getNombre());
        }
        return habito.getUnidad().trim();
    }

    private BigDecimal valorNumerico(Habito habito) {
        if (habito.getValor() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Falta el valor numérico del hábito " + habito.getNombre());
        }
        return habito.getValor();
    }

    private ResponseStatusException datoInvalido(String nombre, String valor) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Valor inválido para " + nombre + ": " + valor);
    }

    private String normalizarCategoria(String nombre) {
        return Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private HuellaCarbonoRespuestaDTO convertirADTO(HuellaCarbono huellaCarbono) {
        return new HuellaCarbonoRespuestaDTO(
                huellaCarbono.getUsuario().getId(), huellaCarbono.getCo2Total(),
                huellaCarbono.getCo2Transporte(), huellaCarbono.getCo2Energia(),
                huellaCarbono.getCo2Alimentacion(), huellaCarbono.getCo2Residuos(),
                huellaCarbono.getFechaCalculo()
        );
    }

    @Override
    public HuellaCarbonoRespuestaDTO buscarPorUsuario(Long usuarioId) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        HuellaCarbono huellaCarbono = huellaCarbonoRepository
                .findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(usuarioId);
        if (huellaCarbono == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Huella de carbono no encontrada");
        }

        return new HuellaCarbonoRespuestaDTO(
                huellaCarbono.getUsuario().getId(),
                huellaCarbono.getCo2Total(),
                huellaCarbono.getCo2Transporte(),
                huellaCarbono.getCo2Energia(),
                huellaCarbono.getCo2Alimentacion(),
                huellaCarbono.getCo2Residuos(),
                huellaCarbono.getFechaCalculo()
        );
    }

    @Override
    public List<HuellaCarbonoRespuestaDTO> obtenerHistorial(Long usuarioId) {
        return huellaCarbonoRepository.findByUsuario_IdOrderByFechaCalculoDescIdDesc(usuarioId)
                .stream()
                .map(this::convertirADTO)
                .toList();
    }

    @Override
    public HuellaEquivalenciasDTO obtenerEquivalencias(Long usuarioId) {
        HuellaCarbono huellaCarbono = huellaCarbonoRepository
                .findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(usuarioId);
        if (huellaCarbono == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Huella de carbono no encontrada");
        }

        BigDecimal co2Total = huellaCarbono.getCo2Total();
        return new HuellaEquivalenciasDTO(
                huellaCarbono.getUsuario().getId(),
                co2Total,
                equivalencia(co2Total, 10),
                equivalencia(co2Total, 50),
                equivalencia(co2Total, 7)
        );
    }

    @Override
    public HuellaCarbonoRespuestaDTO obtenerHuellaDiaria(Long usuarioId) {
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicio = hoy.atStartOfDay();
        LocalDateTime fin = hoy.plusDays(1).atStartOfDay();

        HuellaCarbono huellaCarbono = huellaCarbonoRepository
                .findTopByUsuario_IdAndFechaCalculoBetweenOrderByFechaCalculoDescIdDesc(
                        usuarioId, inicio, fin);
        if (huellaCarbono == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No existe una huella de carbono registrada para el día actual");
        }

        return convertirADTO(huellaCarbono);
    }

    @Override
    public ComparacionPromedioDTO compararConPromedio(Long usuarioId) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        HuellaCarbono ultimaHuella = huellaCarbonoRepository
                .findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(usuarioId);
        if (ultimaHuella == null || ultimaHuella.getCo2Total() == null
                || ultimaHuella.getCo2Total().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "El usuario no tiene una huella de carbono calculada");
        }

        BigDecimal huellaUsuario = ultimaHuella.getCo2Total();
        List<BigDecimal> ultimasHuellas = huellaCarbonoRepository
                .buscarUltimasHuellasValidasPorUsuario();
        long usuariosConDatos = ultimasHuellas.size();
        if (usuariosConDatos < 2) {
            return new ComparacionPromedioDTO(
                    false, huellaUsuario, null, null, null, null,
                    usuariosConDatos, UNIDAD_HUELLA, PERIODO_HUELLA,
                    "No hay suficientes usuarios con cálculos válidos para realizar la comparación");
        }

        BigDecimal promedioExacto = ultimasHuellas.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(usuariosConDatos), 10, RoundingMode.HALF_UP);
        int comparacion = huellaUsuario.compareTo(promedioExacto);
        String posicion = comparacion < 0 ? "POR_DEBAJO_DEL_PROMEDIO"
                : comparacion > 0 ? "POR_ENCIMA_DEL_PROMEDIO"
                : "IGUAL_AL_PROMEDIO";
        BigDecimal diferenciaExacta = huellaUsuario.subtract(promedioExacto).abs();
        BigDecimal diferenciaAbsoluta = diferenciaExacta.setScale(2, RoundingMode.HALF_UP);
        BigDecimal diferenciaPorcentual = promedioExacto.compareTo(BigDecimal.ZERO) == 0
                ? null
                : diferenciaExacta.multiply(BigDecimal.valueOf(100))
                .divide(promedioExacto, 2, RoundingMode.HALF_UP);

        String mensaje = switch (posicion) {
            case "POR_DEBAJO_DEL_PROMEDIO" -> "Tu impacto ambiental es menor que el promedio general";
            case "POR_ENCIMA_DEL_PROMEDIO" -> "Tu impacto ambiental es mayor que el promedio general";
            default -> "Tu impacto ambiental es igual al promedio general";
        };

        return new ComparacionPromedioDTO(
                true,
                huellaUsuario.setScale(2, RoundingMode.HALF_UP),
                promedioExacto.setScale(2, RoundingMode.HALF_UP),
                posicion,
                diferenciaAbsoluta,
                diferenciaPorcentual,
                usuariosConDatos,
                UNIDAD_HUELLA,
                PERIODO_HUELLA,
                mensaje);
    }

    private Long equivalencia(BigDecimal co2Total, int factor) {
        return Math.max(1, co2Total.multiply(BigDecimal.valueOf(factor))
                .setScale(0, RoundingMode.HALF_UP).longValue());
    }

    @Override
    public HuellaDesgloseDTO obtenerDesglose(Long usuarioId) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        HuellaCarbono huellaCarbono = huellaCarbonoRepository
                .findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(usuarioId);
        if (huellaCarbono == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Huella de carbono no encontrada");
        }

        List<ActividadHuellaDTO> actividades = new ArrayList<>();
        actividades.add(new ActividadHuellaDTO("Transporte", huellaCarbono.getCo2Transporte()));
        actividades.add(new ActividadHuellaDTO("Energía", huellaCarbono.getCo2Energia()));
        actividades.add(new ActividadHuellaDTO("Alimentación", huellaCarbono.getCo2Alimentacion()));
        actividades.add(new ActividadHuellaDTO("Residuos", huellaCarbono.getCo2Residuos()));
        actividades.sort(Comparator.comparing(ActividadHuellaDTO::getCo2).reversed());

        return new HuellaDesgloseDTO(
                huellaCarbono.getUsuario().getId(),
                huellaCarbono.getCo2Total(),
                actividades
        );
    }
}
