package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.entities.Habito;
import com.huellago.backend.entities.HuellaCarbono;
import com.huellago.backend.repositories.HabitoRepository;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import com.huellago.backend.services.ContextoRecomendacionesIAService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/** Lectura y transformación únicamente; no invoca inferencia, eventos ni escrituras. */
@Service
public class ContextoRecomendacionesIAServiceImpl implements ContextoRecomendacionesIAService {
    private static final Map<String, List<String>> CAMPOS = campos();
    private final HabitoRepository habitoRepository;
    private final HuellaCarbonoRepository huellas;

    /** Compatibilidad de consumidores anteriores que construyen solo contexto de habitos. */
    public ContextoRecomendacionesIAServiceImpl(HabitoRepository habitoRepository) {
        this(habitoRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ContextoRecomendacionesIAServiceImpl(HabitoRepository habitoRepository, HuellaCarbonoRepository huellas) {
        this.habitoRepository = habitoRepository;
        this.huellas = huellas;
    }

    @Override
    @Transactional(readOnly = true)
    public Resultado construir(Long usuarioId) {
        if (usuarioId == null || usuarioId <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador de usuario debe ser positivo");
        Map<String, Map<String, Habito>> seleccion = new HashMap<>();
        Comparator<Habito> orden = Comparator.comparing(ContextoRecomendacionesIAServiceImpl::fecha,
                Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Habito::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
        for (Habito h : habitoRepository.findByUsuario_Id(usuarioId)) {
            // Defensa adicional: nunca transformar registros de otro propietario, incluso ante un resultado incorrecto.
            if (h == null || h.getUsuario() == null || !usuarioId.equals(h.getUsuario().getId())
                    || h.getCategoria() == null) continue;
            String categoria = normalizar(h.getCategoria().getNombre());
            String campo = normalizar(h.getNombre());
            if (!CAMPOS.containsKey(categoria) || CAMPOS.get(categoria).stream()
                    .noneMatch(c -> normalizar(c).equals(campo))) continue;
            seleccion.computeIfAbsent(categoria, k -> new HashMap<>()).merge(campo, h,
                    (anterior, actual) -> orden.compare(actual, anterior) > 0 ? actual : anterior);
        }

        List<String> incompletas = new ArrayList<>();
        List<String> observaciones = new ArrayList<>();
        Map<String, Map<String, Habito>> validas = new HashMap<>();
        CAMPOS.forEach((categoria, campos) -> {
            Map<String, Habito> datos = seleccion.getOrDefault(categoria, Map.of());
            int erroresPrevios = observaciones.size();
            for (String campo : campos) {
                Habito h = datos.get(normalizar(campo));
                if (campo.equals("consumoKwhDiario") && h == null) continue;
                if (h == null) {
                    observaciones.add(nombreCategoria(categoria) + "." + campo + ": dato obligatorio ausente");
                    continue;
                }
                // Copia escalar: normalizar sin modificar la entidad administrada ni sus datos históricos.
                Habito candidato = new Habito();
                candidato.setNombre(campo);
                candidato.setValor(h.getValor());
                candidato.setUnidad(normalizar(h.getUnidad()));
                try {
                    ValidacionHabitosIniciales.validarCampo(List.of(candidato), campo);
                    if (campo.equals("personas") || campo.equals("diasSemana")) h.getValor().intValueExact();
                } catch (ResponseStatusException | ArithmeticException error) {
                    observaciones.add(nombreCategoria(categoria) + "." + campo + ": valor o unidad inválidos");
                }
            }
            if (categoria.equals("energia") && datos.containsKey("consumokwhdiario")
                    && (!datos.containsKey("fuente") || !normalizar(datos.get("fuente").getUnidad()).equals("electricidad")))
                observaciones.add("Energía.consumoKwhDiario: requiere fuente Electricidad");
            if (observaciones.size() == erroresPrevios) validas.put(categoria, datos);
            else incompletas.add(nombreCategoria(categoria));
        });

        var t = validas.get("transporte");
        var e = validas.get("energia");
        var a = validas.get("alimentacion");
        var r = validas.get("residuos");
        var contexto = new ContextoRecomendacionesIADTO(
                t == null ? null : new ContextoRecomendacionesIADTO.Transporte(texto(t, "medio"),
                        t.get("kmsemana").getValor(), t.get("diassemana").getValor().intValueExact()),
                e == null ? null : new ContextoRecomendacionesIADTO.Energia(texto(e, "vivienda"),
                        e.get("personas").getValor().intValueExact(), texto(e, "fuente"),
                        e.containsKey("consumokwhdiario") ? e.get("consumokwhdiario").getValor() : null),
                a == null ? null : new ContextoRecomendacionesIADTO.Alimentacion(texto(a, "tipo")),
                r == null ? null : new ContextoRecomendacionesIADTO.Residuos(texto(r, "plasticos"),
                        r.get("reciclas").getValor().signum() != 0));
        return new Resultado(contexto, incompletas, observaciones, ReglasInterpretacionAmbiental.REGLAS,
                huellas == null ? null : resumen(usuarioId, seleccion, incompletas, contexto));
    }

    private ResumenHuella resumen(Long usuarioId, Map<String, Map<String, Habito>> seleccion,
            List<String> incompletas, ContextoRecomendacionesIADTO contexto) {
        var limites = EnumSet.of(Limitacion.MODELO_ORIENTATIVO_NO_CERTIFICADO,
                Limitacion.ORIGEN_HISTORICO_NO_TRAZADO, Limitacion.METODOLOGIA_Y_ATRIBUCION_PENDIENTES);
        if (!incompletas.isEmpty()) limites.add(Limitacion.CONTEXTO_PARCIAL);
        HuellaCarbono h = huellas.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(usuarioId);
        if (h == null) {
            limites.add(Limitacion.SIN_HUELLA);
            return new ResumenHuella(null, null, null, Map.of(), Vigencia.INEXISTENTE,
                    Comparabilidad.NO_ACREDITADA, Map.of(), limites);
        }
        if (h.getUsuario() == null || !usuarioId.equals(h.getUsuario().getId()) || h.getId() == null || h.getId() <= 0) {
            limites.add(Limitacion.IDENTIDAD_O_PROPIETARIO_INVALIDO);
            // No trasladar valores, identificadores o fechas de otro propietario, ni siquiera a la captura.
            return new ResumenHuella(null, null, null, Map.of(), Vigencia.INVALIDA,
                    Comparabilidad.NO_ACREDITADA, Map.of(), limites);
        }
        Map<String, java.math.BigDecimal> componentes = new LinkedHashMap<>();
        java.math.BigDecimal[] valores = {h.getCo2Transporte(), h.getCo2Energia(), h.getCo2Alimentacion(), h.getCo2Residuos()};
        String[] nombres = {"Transporte", "Energía", "Alimentación", "Residuos"};
        for (int i = 0; i < nombres.length; i++) {
            if (valores[i] == null) limites.add(Limitacion.COMPONENTES_AUSENTES);
            else if (valores[i].signum() < 0) limites.add(Limitacion.VALORES_INVALIDOS);
            else componentes.put(nombres[i], valores[i]); // Cero es dato; ausencia no es cero.
        }
        if (h.getCo2Total() == null) limites.add(Limitacion.COMPONENTES_AUSENTES);
        else if (h.getCo2Total().signum() < 0) limites.add(Limitacion.VALORES_INVALIDOS);
        if (h.getFechaCalculo() == null) limites.add(Limitacion.FECHA_INVALIDA);
        boolean invalida = limites.contains(Limitacion.COMPONENTES_AUSENTES) || limites.contains(Limitacion.VALORES_INVALIDOS)
                || limites.contains(Limitacion.FECHA_INVALIDA);
        var habitos = seleccion.values().stream().flatMap(c -> c.values().stream()).toList();
        if (habitos.isEmpty() || habitos.stream().anyMatch(c -> fecha(c) == null))
            limites.add(Limitacion.FECHAS_HABITOS_NO_VERIFICABLES);
        if (h.getFechaCalculo() != null && habitos.stream().anyMatch(c -> fecha(c) != null && fecha(c).isAfter(h.getFechaCalculo())))
            limites.add(Limitacion.HABITOS_POSTERIORES);
        Vigencia vigencia = invalida ? Vigencia.INVALIDA : limites.contains(Limitacion.HABITOS_POSTERIORES) ? Vigencia.OBSOLETA
                : limites.contains(Limitacion.FECHAS_HABITOS_NO_VERIFICABLES) ? Vigencia.NO_VERIFICABLE : Vigencia.VIGENTE;
        Map<String, Perimetro> perimetros = new LinkedHashMap<>();
        componentes.keySet().forEach(c -> perimetros.put(c, c.equals("Energía") ? Perimetro.NO_ACREDITADO : Perimetro.REGISTRO_USUARIO_NO_ACREDITADO));
        boolean hogar = componentes.containsKey("Energía") && contexto.energia() != null && contexto.energia().consumoKwhDiario() != null;
        if (hogar) {
            // Describe el ambito del dato actual, no certifica la formula aplicada a una fila historica.
            perimetros.put("Energía", Perimetro.HOGAR_SEGUN_CONTEXTO);
            limites.add(Limitacion.PERIMETRO_HOGAR_NO_ATRIBUIBLE_AL_USUARIO);
        }
        return new ResumenHuella(h.getId(), h.getFechaCalculo(), h.getCo2Total() != null && h.getCo2Total().signum() >= 0 ? h.getCo2Total() : null,
                componentes, vigencia, hogar && componentes.size() > 1 ? Comparabilidad.PERIMETROS_NO_COMPARABLES : Comparabilidad.NO_ACREDITADA,
                perimetros, limites);
        // No comparar el total historico con la suma: puede tener redondeo independiente legitimo.
    }

    private static Map<String, List<String>> campos() {
        Map<String, List<String>> campos = new LinkedHashMap<>();
        campos.put("transporte", List.of("medio", "kmSemana", "diasSemana"));
        campos.put("energia", List.of("vivienda", "personas", "fuente", "consumoKwhDiario"));
        campos.put("alimentacion", List.of("tipo"));
        campos.put("residuos", List.of("plasticos", "reciclas"));
        return Collections.unmodifiableMap(campos);
    }

    private static String nombreCategoria(String categoria) {
        return switch (categoria) {
            case "transporte" -> "Transporte";
            case "energia" -> "Energía";
            case "alimentacion" -> "Alimentación";
            case "residuos" -> "Residuos";
            default -> throw new IllegalArgumentException("Categoría no admitida");
        };
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : ValidacionHabitosIniciales.normalizar(valor);
    }

    private static String texto(Map<String, Habito> campos, String campo) {
        String texto = normalizar(campos.get(campo).getUnidad());
        return texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
    }

    private static LocalDateTime fecha(Habito habito) {
        if (habito.getFechaActualizacion() == null) return habito.getFechaRegistro();
        return habito.getFechaRegistro() != null && habito.getFechaRegistro().isAfter(habito.getFechaActualizacion())
                ? habito.getFechaRegistro() : habito.getFechaActualizacion();
    }
}
