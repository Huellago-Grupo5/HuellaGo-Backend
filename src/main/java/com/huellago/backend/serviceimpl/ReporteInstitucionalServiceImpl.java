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

    public ReporteInstitucionalServiceImpl(InstitucionRepository instituciones,
            AutorizacionInstitucionalService autorizacion, RetoRepository retos,
            UsuarioRetoRepository participaciones, ResultadoRetoColectivoRepository resultados) {
        this.instituciones = instituciones;
        this.autorizacion = autorizacion;
        this.retos = retos;
        this.participaciones = participaciones;
        this.resultados = resultados;
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
        return new ReporteInstitucionalDTO(new ReporteInstitucionalDTO.InstitucionDatos(
                institucion.getId(), institucion.getNombre()), fecha,
                new ReporteInstitucionalDTO.Filtros(desde, hasta, campanaId),
                resumen(campanas, total, usuarios), campanas, advertencias);
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
        String estado = CalculoSeguimientoColectivo.estadoCampana(datos, hoy);
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
