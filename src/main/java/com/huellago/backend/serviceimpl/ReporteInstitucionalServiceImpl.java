package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ReporteInstitucionalDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.repositories.projections.*;
import com.huellago.backend.security.AutorizacionInstitucionalService;
import com.huellago.backend.services.ReporteInstitucionalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReporteInstitucionalServiceImpl implements ReporteInstitucionalService {
    private static final int LOTE = 250;
    private final InstitucionRepository instituciones;
    private final AutorizacionInstitucionalService autorizacion;
    private final RetoRepository retos;
    private final UsuarioRetoRepository participaciones;
    private final ResultadoRetoColectivoRepository resultados;
    private final UsuarioInstitucionRepository afiliaciones;
    private final HuellaCarbonoRepository huellas;

    public ReporteInstitucionalServiceImpl(InstitucionRepository instituciones,
            AutorizacionInstitucionalService autorizacion, RetoRepository retos,
            UsuarioRetoRepository participaciones, ResultadoRetoColectivoRepository resultados,
            UsuarioInstitucionRepository afiliaciones, HuellaCarbonoRepository huellas) {
        this.instituciones = instituciones;
        this.autorizacion = autorizacion;
        this.retos = retos;
        this.participaciones = participaciones;
        this.resultados = resultados;
        this.afiliaciones = afiliaciones;
        this.huellas = huellas;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReporteInstitucionalDTO consultar(Long institucionId, Usuario usuario,
            LocalDate desde, LocalDate hasta, Integer campanaId) {
        if (usuario == null || usuario.getId() == null) throw error(401, "Se requiere autenticación");
        validar(institucionId, desde, hasta, campanaId);
        Institucion institucion = instituciones.findById(institucionId)
                .orElseThrow(() -> error(404, "Institución no encontrada"));
        autorizacion.exigirAdministradorConsulta(usuario.getId(), institucionId);
        if (campanaId != null && !retos.existsByIdAndInstitucion_IdAndTipo(
                campanaId, institucionId, TipoReto.COLECTIVO)) {
            throw error(404, "Campaña no encontrada en esta institución");
        }
        LocalDateTime fecha = LocalDateTime.now();
        List<ReporteInstitucionalDTO.Campana> campanas = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        Page<CampanaReporteVista> pagina;
        int numero = 0;
        do {
            pagina = retos.buscarParaReporte(institucionId, desde, hasta, campanaId,
                    PageRequest.of(numero++, LOTE, Sort.by("id")));
            if (!pagina.isEmpty()) {
                List<Integer> ids = pagina.stream().map(CampanaReporteVista::getId).toList();
                Map<Integer, ParticipacionReporteVista> avances = participaciones.resumirParaReporte(ids).stream()
                        .collect(Collectors.toMap(ParticipacionReporteVista::getRetoId, Function.identity()));
                Map<Integer, ResultadoReporteVista> cierres = resultados.buscarParaReporte(ids).stream()
                        .collect(Collectors.toMap(ResultadoReporteVista::getRetoId, Function.identity()));
                for (CampanaReporteVista campana : pagina) {
                    campanas.add(campana(campana, avances.get(campana.getId()), cierres.get(campana.getId()),
                            fecha.toLocalDate(), advertencias));
                }
            }
        } while (pagina.hasNext());

        long total = 0;
        long usuarios = 0;
        if (campanas.isEmpty()) {
            advertencias.add("No hay campañas para los filtros solicitados");
        } else {
            TotalesParticipacionVista totales = participaciones.totalesParaReporte(institucionId, desde, hasta, campanaId);
            if (totales == null || totales.getTotal() == null || totales.getUsuarios() == null) {
                throw error(500, "No se pudieron obtener los totales del reporte");
            }
            total = totales.getTotal();
            usuarios = totales.getUsuarios();
        }
        var historialHuellas = huellas.historialInstitucional(institucionId);
        var huella = huella(institucionId, historialHuellas);
        return new ReporteInstitucionalDTO(new ReporteInstitucionalDTO.InstitucionDatos(
                institucion.getId(), institucion.getNombre()), fecha,
                new ReporteInstitucionalDTO.Filtros(desde, hasta, campanaId),
                resumen(campanas, total, usuarios), campanas, advertencias, huella,
                seguimiento(historialHuellas, desde, hasta));
    }

    private ReporteInstitucionalDTO.HuellaPromedio huella(Long institucionId,
            List<HuellaCarbonoRepository.FilaInstitucional> filas) {
        long miembros = afiliaciones.countByInstitucion_Id(institucionId);
        Set<Long> observados = new HashSet<>();
        long conCalculo = 0;
        long considerados = 0;
        BigDecimal suma = BigDecimal.ZERO;
        for (var fila : filas) {
            if (fila.getUsuarioId() == null || !observados.add(fila.getUsuarioId())) continue;
            conCalculo++;
            if (!completa(fila)) continue;
            considerados++;
            suma = suma.add(fila.getCo2Total());
        }
        // Evita que un promedio de uno o dos miembros revele una huella individual.
        BigDecimal promedio = considerados < 3 ? null
                : suma.divide(BigDecimal.valueOf(considerados), 2, RoundingMode.HALF_UP);
        String limitaciones = "Promedio orientativo de la última huella semanal completa por miembro; "
                + "alcance mixto personal y hogar. No atribuye toda la energía del hogar al individuo. "
                + "El registro histórico no conserva versión metodológica ni acredita vigencia frente a hábitos posteriores. "
                + "El promedio se muestra solo con al menos tres miembros con huella completa.";
        return new ReporteInstitucionalDTO.HuellaPromedio(miembros, conCalculo, considerados,
                promedio, "kg CO2e", "semanal", considerados < 3 ? "COHORTE_INSUFICIENTE"
                        : "ORIENTATIVO_NO_CERTIFICADO", limitaciones);
    }

    private record ClaveMetodo(String version, String periodo, String unidad,
            String perimetro, String factores) { }
    private record ParHuella(HuellaCarbonoRepository.FilaInstitucional inicial,
            HuellaCarbonoRepository.FilaInstitucional finalizada) { }

    private ReporteInstitucionalDTO.SeguimientoImpacto seguimiento(
            List<HuellaCarbonoRepository.FilaInstitucional> filas, LocalDate desde, LocalDate hasta) {
        Map<Long, List<HuellaCarbonoRepository.FilaInstitucional>> porUsuario = new HashMap<>();
        for (var fila : filas) {
            if (fila.getUsuarioId() == null || !completa(fila) || !enPeriodo(fila.getFechaCalculo(), desde, hasta))
                continue;
            porUsuario.computeIfAbsent(fila.getUsuarioId(), ignorado -> new ArrayList<>()).add(fila);
        }
        long paresCompletos = porUsuario.values().stream()
                .filter(historial -> historial.stream().map(HuellaCarbonoRepository.FilaInstitucional::getFechaCalculo)
                        .distinct().limit(2).count() == 2).count();
        Map<ClaveMetodo, List<ParHuella>> cohortes = new HashMap<>();
        for (var historial : porUsuario.values()) {
            var finalizada = historial.stream().filter(fila -> clave(fila) != null).findFirst().orElse(null);
            if (finalizada == null) continue;
            ClaveMetodo clave = clave(finalizada);
            HuellaCarbonoRepository.FilaInstitucional inicial = null;
            for (var fila : historial)
                if (clave.equals(clave(fila)) && fila.getFechaCalculo().isBefore(finalizada.getFechaCalculo()))
                    inicial = fila;
            if (inicial != null)
                cohortes.computeIfAbsent(clave, ignorado -> new ArrayList<>())
                        .add(new ParHuella(inicial, finalizada));
        }
        var seleccion = cohortes.entrySet().stream()
                .sorted(Comparator.<Map.Entry<ClaveMetodo, List<ParHuella>>>comparingInt(
                        entrada -> entrada.getValue().size()).reversed()
                        .thenComparing(entrada -> entrada.getKey().toString()))
                .findFirst().orElse(null);
        String limites = "Estimación orientativa de huella semanal de alcance mixto personal y hogar; "
                + "solo compara pares del mismo miembro, versión, unidad, período, perímetro y factores. "
                + "La variación no demuestra que una campaña haya causado el cambio.";
        if (seleccion == null || seleccion.getValue().size() < 3)
            return new ReporteInstitucionalDTO.SeguimientoImpacto(paresCompletos, 0, null, null,
                    paresCompletos == 0 ? "DATOS_INSUFICIENTES"
                            : seleccion == null ? "METODOLOGIA_NO_VERIFICABLE" : "COHORTE_INSUFICIENTE",
                    limites, null, null);

        BigDecimal inicial = BigDecimal.ZERO, finalizada = BigDecimal.ZERO;
        for (var par : seleccion.getValue()) {
            inicial = inicial.add(par.inicial().getCo2Total());
            finalizada = finalizada.add(par.finalizada().getCo2Total());
        }
        BigDecimal variacion = finalizada.subtract(inicial);
        BigDecimal porcentaje = inicial.signum() == 0 ? null : variacion
                .multiply(BigDecimal.valueOf(100)).divide(inicial, 2, RoundingMode.HALF_UP);
        String estado = inicial.signum() == 0 ? "BASE_INICIAL_CERO"
                : variacion.signum() < 0 ? "REDUCCION_ORIENTATIVA"
                : variacion.signum() > 0 ? "AUMENTO_ORIENTATIVO" : "SIN_CAMBIO";
        return new ReporteInstitucionalDTO.SeguimientoImpacto(paresCompletos,
                seleccion.getValue().size(), variacion, porcentaje, estado, limites,
                inicial, finalizada);
    }

    private boolean enPeriodo(LocalDateTime fecha, LocalDate desde, LocalDate hasta) {
        LocalDate dia = fecha.toLocalDate();
        return (desde == null || !dia.isBefore(desde)) && (hasta == null || !dia.isAfter(hasta));
    }

    private ClaveMetodo clave(HuellaCarbonoRepository.FilaInstitucional fila) {
        if (!"HUELLAGO_SEMANAL_V1".equals(fila.getMetodologiaVersion())
                || !"semanal".equals(fila.getPeriodoCalculo())
                || !"kg CO2e".equals(fila.getUnidadCalculo())
                || !"PERSONAL_Y_HOGAR".equals(fila.getPerimetroCalculo())
                || fila.getFactoresAplicados() == null || fila.getFactoresAplicados().isBlank()) return null;
        return new ClaveMetodo(fila.getMetodologiaVersion(), fila.getPeriodoCalculo(),
                fila.getUnidadCalculo(), fila.getPerimetroCalculo(), fila.getFactoresAplicados());
    }

    private boolean completa(HuellaCarbonoRepository.FilaInstitucional fila) {
        return fila.getFechaCalculo() != null && noNegativo(fila.getCo2Total())
                && noNegativo(fila.getCo2Transporte()) && noNegativo(fila.getCo2Energia())
                && noNegativo(fila.getCo2Alimentacion()) && noNegativo(fila.getCo2Residuos());
    }

    private boolean noNegativo(BigDecimal valor) {
        return valor != null && valor.signum() >= 0;
    }

    private ReporteInstitucionalDTO.Campana campana(CampanaReporteVista campana,
            ParticipacionReporteVista avance, ResultadoReporteVista resultado, LocalDate hoy,
            List<String> advertencias) {
        long cantidad = avance == null ? 0 : avance.getParticipantes();
        BigDecimal suma = avance == null ? BigDecimal.ZERO : avance.getSumaProgreso();
        var datos = new CalculoSeguimientoColectivo.Datos(campana.getId(), campana.getTitulo(),
                campana.getFechaInicio(), campana.getFechaFin(), campana.getActivo());
        var cierre = resultado == null ? null : new CalculoSeguimientoColectivo.Cierre(
                resultado.getParticipantes(), resultado.getProgresoFinal(), resultado.getEstado(), resultado.getFechaCierre());
        var seguimiento = CalculoSeguimientoColectivo.resumen(datos, Math.toIntExact(cantidad), suma, cierre, hoy);
        String estado = CalculoSeguimientoColectivo.estadoCampana(datos, cierre, hoy);
        if ("DATOS_INCOMPLETOS".equals(estado)) advertencias.add("Campaña " + campana.getId() + ": fechas incompletas");
        if (resultado != null && !Objects.equals(resultado.getParticipantes(), Math.toIntExact(cantidad))) {
            advertencias.add("Campaña " + campana.getId() + ": las participaciones actuales difieren del cierre conservado");
        }
        String resultadoFinal = resultado != null && ("EXITOSO".equals(resultado.getEstado())
                || "NO_ALCANZADO".equals(resultado.getEstado())) ? resultado.getEstado() : null;
        return new ReporteInstitucionalDTO.Campana(campana.getId(), campana.getTitulo(), campana.getDescripcion(),
                campana.getFechaInicio(), campana.getFechaFin(), estado, seguimiento.getEstado(), cantidad,
                seguimiento.getParticipantes(), seguimiento.getProgresoColectivo(), resultadoFinal, seguimiento.getFechaCierre());
    }

    private ReporteInstitucionalDTO.Resumen resumen(List<ReporteInstitucionalDTO.Campana> campanas,
                                                   long total, long usuarios) {
        int futuras = 0, activas = 0, finalizadas = 0, noDisponibles = 0, incompletas = 0;
        int conResultado = 0, exitosas = 0, noAlcanzadas = 0;
        BigDecimal sumaPonderada = BigDecimal.ZERO;
        long peso = 0;
        boolean progresoValido = true;
        for (var campana : campanas) {
            switch (campana.estado()) {
                case "FUTURA" -> futuras++;
                case "ACTIVA" -> activas++;
                case "FINALIZADA" -> finalizadas++;
                case "NO_DISPONIBLE" -> noDisponibles++;
                default -> incompletas++;
            }
            if (campana.resultadoFinal() != null) {
                conResultado++;
                if ("EXITOSO".equals(campana.resultadoFinal())) exitosas++;
                else noAlcanzadas++;
            }
            Integer cantidad = campana.participantes();
            BigDecimal progreso = campana.progresoColectivo();
            if (cantidad == null || cantidad < 0 || progreso == null || progreso.signum() < 0
                    || progreso.compareTo(BigDecimal.valueOf(100)) > 0) {
                progresoValido = false;
            } else if (cantidad > 0) {
                peso = Math.addExact(peso, cantidad);
                sumaPonderada = sumaPonderada.add(progreso.multiply(BigDecimal.valueOf(cantidad)));
            }
        }
        BigDecimal general = peso == 0 || !progresoValido ? null
                : sumaPonderada.divide(BigDecimal.valueOf(peso), 2, RoundingMode.HALF_UP);
        return new ReporteInstitucionalDTO.Resumen(campanas.size(), futuras, activas, finalizadas,
                noDisponibles, incompletas, total, usuarios, peso, general, conResultado, exitosas, noAlcanzadas);
    }

    private void validar(Long institucionId, LocalDate desde, LocalDate hasta, Integer campanaId) {
        if (institucionId == null || institucionId <= 0 || campanaId != null && campanaId <= 0) {
            throw error(400, "Los identificadores deben ser positivos");
        }
        if (desde != null && (desde.getYear() < 1 || desde.getYear() > 9999)
                || hasta != null && (hasta.getYear() < 1 || hasta.getYear() > 9999)
                || desde != null && hasta != null && hasta.isBefore(desde)) {
            throw error(400, "Periodo inválido: usa años entre 1 y 9999 y hasta igual o posterior a desde");
        }
    }

    private ResponseStatusException error(int estado, String mensaje) {
        return new ResponseStatusException(HttpStatus.valueOf(estado), mensaje);
    }
}
