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

    @Autowired
    PoliticaMedicionInicial politica;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("huellaConsultaClock")
    java.time.Clock relojConsulta;

    private static final String UNIDAD_HUELLA = "kg CO2e";
    private static final String PERIODO_HUELLA = "semanal";
    private static final BigDecimal CO2_ARBOL_KG_ANUAL = new BigDecimal("60");
    private static final BigDecimal CO2_AUTO_KG_MILLA = new BigDecimal("0.393");
    private static final BigDecimal KM_POR_MILLA = new BigDecimal("1.609344");
    private static final BigDecimal CO2_RECICLAJE_KG_POR_TONELADA_CORTA = new BigDecimal("2830");
    private static final BigDecimal KG_POR_TONELADA_CORTA = new BigDecimal("907.18474");

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

        usuarioRepository.bloquearParaRecomendaciones(usuarioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
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
        List<String> incompletas = ValidacionHabitosIniciales.categoriasPendientes(habitos);
        if (!incompletas.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Categorías incompletas o inválidas: " + String.join(", ", incompletas));

        // numeric(38,2): normaliza solo los componentes finales de las huellas nuevas.
        // El total suma estos mismos importes, sin redondear las operaciones intermedias.
        BigDecimal co2Transporte = calcularTransporte(requerirCategoria(habitosPorCategoria, "transporte"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal co2Energia = calcularEnergia(requerirCategoria(habitosPorCategoria, "energia"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal co2Alimentacion = calcularAlimentacion(requerirCategoria(habitosPorCategoria, "alimentacion"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal co2Residuos = calcularResiduos(requerirCategoria(habitosPorCategoria, "residuos"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal co2Total = co2Transporte.add(co2Energia).add(co2Alimentacion).add(co2Residuos);

        politica.nivel(co2Total); // Valida la politica antes de intentar persistir.
        HuellaCarbono huellaCarbono = new HuellaCarbono();
        huellaCarbono.setUsuario(usuario);
        huellaCarbono.setCo2Transporte(co2Transporte);
        huellaCarbono.setCo2Energia(co2Energia);
        huellaCarbono.setCo2Alimentacion(co2Alimentacion);
        huellaCarbono.setCo2Residuos(co2Residuos);
        huellaCarbono.setCo2Total(co2Total);
        huellaCarbono.setFechaCalculo(java.time.LocalDateTime.now());
        huellaCarbono.setMetodologiaVersion("HUELLAGO_SEMANAL_V1");
        huellaCarbono.setPeriodoCalculo(PERIODO_HUELLA);
        huellaCarbono.setUnidadCalculo(UNIDAD_HUELLA);
        huellaCarbono.setPerimetroCalculo("PERSONAL_Y_HOGAR");
        huellaCarbono.setFactoresAplicados(factoresAplicados());

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
        ValidacionHabitosIniciales.validarCampo(habitos, nombre);
    }

    private String factoresAplicados() {
        // Identidad exacta de los coeficientes del cálculo vigente; no incluye equivalencias EPA.
        return "transporte.bicicleta=" + FACTOR_MEDIO_BICICLETA
                + ";transporte.caminando=" + FACTOR_MEDIO_CAMINANDO
                + ";transporte.bus=" + FACTOR_MEDIO_BUS
                + ";transporte.auto=" + FACTOR_MEDIO_AUTO
                + ";transporte.moto=" + FACTOR_MEDIO_MOTO
                + ";energia.casa=" + FACTOR_VIVIENDA_CASA
                + ";energia.departamento=" + FACTOR_VIVIENDA_DEPARTAMENTO
                + ";energia.electricidad=" + FACTOR_FUENTE_ELECTRICIDAD
                + ";energia.gas=" + FACTOR_FUENTE_GAS
                + ";energia.solar=" + FACTOR_FUENTE_SOLAR
                + ";energia.personas=" + FACTOR_PERSONAS
                + ";energia.kwh=" + politica.factorElectricidad
                + ";alimentacion.vegana=" + FACTOR_TIPO_VEGANA
                + ";alimentacion.vegetariana=" + FACTOR_TIPO_VEGETARIANA
                + ";alimentacion.mixta=" + FACTOR_TIPO_MIXTA
                + ";residuos.bajo=" + FACTOR_PLASTICOS_BAJO
                + ";residuos.medio=" + FACTOR_PLASTICOS_MEDIO
                + ";residuos.alto=" + FACTOR_PLASTICOS_ALTO
                + ";residuos.recicla=" + FACTOR_RECICLA;
    }

    private BigDecimal calcularTransporte(List<Habito> habitos) {
        String medio = valorTexto(requerirHabito(habitos, "medio"));
        BigDecimal kmSemana = valorNumerico(requerirHabito(habitos, "kmSemana"));
        return emisionesTransporteSemanal(medio, kmSemana);
    }

    /** Misma fórmula y coeficientes del cálculo vigente; permite evaluar un escenario hipotético. */
    static BigDecimal emisionesTransporteSemanal(String medio, BigDecimal kmSemana) {
        BigDecimal factor = switch (medio.toLowerCase(Locale.ROOT)) {
            case "bicicleta" -> FACTOR_MEDIO_BICICLETA;
            case "caminando" -> FACTOR_MEDIO_CAMINANDO;
            case "bus" -> FACTOR_MEDIO_BUS;
            case "auto" -> FACTOR_MEDIO_AUTO;
            case "moto" -> FACTOR_MEDIO_MOTO;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Medio de transporte no admitido");
        };
        return factor.multiply(kmSemana);
    }

    private BigDecimal calcularEnergia(List<Habito> habitos) {
        var consumos = habitos.stream().filter(h -> "consumoKwhDiario".equalsIgnoreCase(h.getNombre())).toList();
        if (!consumos.isEmpty()) {
            validarCampo(habitos, "consumoKwhDiario");
            return politica.consumoElectricoSemanal(consumos.get(0).getValor());
        }
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
        HuellaCarbonoRespuestaDTO respuesta = new HuellaCarbonoRespuestaDTO(
                huellaCarbono.getUsuario().getId(), huellaCarbono.getCo2Total(),
                huellaCarbono.getCo2Transporte(), huellaCarbono.getCo2Energia(),
                huellaCarbono.getCo2Alimentacion(), huellaCarbono.getCo2Residuos(),
                huellaCarbono.getFechaCalculo()
        );
        boolean componentesValidos = java.util.stream.Stream.of(huellaCarbono.getCo2Transporte(),
                        huellaCarbono.getCo2Energia(), huellaCarbono.getCo2Alimentacion(), huellaCarbono.getCo2Residuos())
                .allMatch(valor -> valor != null && valor.signum() >= 0);
        respuesta.setNivelImpacto(componentesValidos ? politica.nivel(huellaCarbono.getCo2Total()) : "NO_DEFINIDO");
        respuesta.setReferenciaImpacto(politica.referenciaImpacto());
        return respuesta;
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

        return convertirADTO(huellaCarbono);
    }

    @Override
    public List<HuellaCarbonoRespuestaDTO> obtenerHistorial(Long usuarioId) {
        return huellaCarbonoRepository.findByUsuario_IdOrderByFechaCalculoDescIdDesc(usuarioId)
                .stream()
                .map(this::convertirADTO)
                .toList();
    }

    @Override
    public List<HuellaCarbonoRespuestaDTO> obtenerHistorial(Long usuarioId, LocalDate fecha) {
        if (fecha == null) return obtenerHistorial(usuarioId);
        IntervaloConsulta intervalo = intervaloConsulta(fecha);
        return huellaCarbonoRepository
                .findByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                        usuarioId, intervalo.inicio(), intervalo.fin())
                .stream().map(this::convertirADTO).toList();
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
        if (co2Total == null || co2Total.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No hay una huella de carbono válida para mostrar equivalencias");
        }
        return new HuellaEquivalenciasDTO(
                huellaCarbono.getUsuario().getId(),
                co2Total,
                equivalencia(co2Total.multiply(BigDecimal.valueOf(365)),
                        CO2_ARBOL_KG_ANUAL.multiply(BigDecimal.valueOf(7))),
                equivalencia(co2Total.multiply(KM_POR_MILLA), CO2_AUTO_KG_MILLA),
                equivalencia(co2Total.multiply(KG_POR_TONELADA_CORTA), CO2_RECICLAJE_KG_POR_TONELADA_CORTA)
        );
    }

    @Override
    public HuellaCarbonoRespuestaDTO obtenerHuellaDiaria(Long usuarioId) {
        return obtenerHuellaDiaria(usuarioId, null);
    }

    @Override
    public HuellaCarbonoRespuestaDTO obtenerHuellaDiaria(Long usuarioId, LocalDate fecha) {
        LocalDate seleccionada = fecha == null
                ? LocalDate.now(relojConsulta.withZone(java.time.ZoneId.of("America/Lima"))) : fecha;
        IntervaloConsulta intervalo = intervaloConsulta(seleccionada);

        HuellaCarbono huellaCarbono = huellaCarbonoRepository
                .findTopByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
                        usuarioId, intervalo.inicio(), intervalo.fin());
        if (huellaCarbono == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No hay cálculos de huella de carbono registrados para la fecha seleccionada.");
        }

        return promedioDiarioEstimado(convertirADTO(huellaCarbono));
    }

    private record IntervaloConsulta(LocalDateTime inicio, LocalDateTime fin) { }

    private HuellaCarbonoRespuestaDTO promedioDiarioEstimado(HuellaCarbonoRespuestaDTO semanal) {
        semanal.setCo2Total(promedioDiario(semanal.getCo2Total()));
        semanal.setCo2Transporte(promedioDiario(semanal.getCo2Transporte()));
        semanal.setCo2Energia(promedioDiario(semanal.getCo2Energia()));
        semanal.setCo2Alimentacion(promedioDiario(semanal.getCo2Alimentacion()));
        semanal.setCo2Residuos(promedioDiario(semanal.getCo2Residuos()));
        semanal.setPeriodo("diario (promedio estimado)");
        semanal.setMetodologia("Promedio diario estimado de la huella semanal calculada en fechaCalculo (valor semanal / 7); "
                + "no es una medición de las emisiones de la fecha consultada. "
                + "Puede incluir consumo energético del hogar. "
                + "Modelo orientativo HuellaGo con coeficientes heredados sin validación científica.");
        semanal.setNivelImpacto("NO_DEFINIDO");
        semanal.setReferenciaImpacto("Sin umbrales diarios aprobados");
        return semanal;
    }

    private BigDecimal promedioDiario(BigDecimal semanal) {
        return semanal == null ? null : semanal.divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
    }

    private IntervaloConsulta intervaloConsulta(LocalDate fecha) {
        // El contrato admite años de cuatro dígitos sin año cero; no transforma fechas históricas.
        if (fecha.getYear() < 1 || fecha.getYear() > 9999)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha está fuera del rango admitido.");
        try {
            return new IntervaloConsulta(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());
        } catch (java.time.DateTimeException | ArithmeticException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha está fuera del rango admitido.");
        }
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

    private Long equivalencia(BigDecimal numerador, BigDecimal denominador) {
        try {
            return numerador.divide(denominador, 0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "La huella de carbono excede el rango admitido para mostrar equivalencias");
        }
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
        // List.sort es estable: los empates conservan el orden canónico de arriba.
        actividades.sort(Comparator.comparing(ActividadHuellaDTO::getCo2,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return new HuellaDesgloseDTO(
                huellaCarbono.getUsuario().getId(),
                huellaCarbono.getCo2Total(),
                actividades
        );
    }
}
