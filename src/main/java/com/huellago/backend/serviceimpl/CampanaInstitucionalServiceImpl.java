package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.AutorizacionInstitucionalService;
import com.huellago.backend.services.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CampanaInstitucionalServiceImpl implements CampanaInstitucionalService {
    private final InstitucionRepository instituciones;
    private final RetoRepository retos;
    private final AutorizacionInstitucionalService autorizacion;
    private final RetoColectivoService colectivos;

    public CampanaInstitucionalServiceImpl(InstitucionRepository instituciones, RetoRepository retos,
            AutorizacionInstitucionalService autorizacion, RetoColectivoService colectivos) {
        this.instituciones = instituciones;
        this.retos = retos;
        this.autorizacion = autorizacion;
        this.colectivos = colectivos;
    }

    @Override
    @Transactional
    public CampanaRespuestaDTO crear(Long institucionId, Usuario usuario, CampanaCrearDTO datos) {
        Long usuarioId = identidad(usuario);
        validarId(institucionId);
        validarDatos(datos);
        instituciones.bloquearCampanas(institucionId).orElseThrow(() -> error(404, "Institución no encontrada"));
        autorizacion.exigirAdministrador(usuarioId, institucionId);
        Institucion institucion = instituciones.findById(institucionId)
                .orElseThrow(() -> error(404, "Institución no encontrada"));
        String nombre = datos.nombre().trim();
        if (retos.existsByInstitucion_IdAndTituloIgnoreCaseAndFechaInicioAndFechaFin(
                institucionId, nombre, datos.fechaInicio(), datos.fechaFin())) {
            throw error(409, "Ya existe una campaña con ese nombre y periodo en la institución");
        }
        Reto reto = new Reto();
        reto.setInstitucion(institucion);
        reto.setTitulo(nombre);
        reto.setDescripcion(datos.objetivo().trim());
        reto.setFechaInicio(datos.fechaInicio());
        reto.setFechaFin(datos.fechaFin());
        reto.setActivo(true);
        reto.setTipo(TipoReto.COLECTIVO);
        reto.setPuntosRecompensa(0);
        return respuesta(retos.saveAndFlush(reto), usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampanaRespuestaDTO> listar(Long institucionId, Usuario usuario, int pagina, int tamano) {
        Long usuarioId = identidad(usuario);
        validarId(institucionId);
        Pageable paginas = pagina(pagina, tamano);
        if (!instituciones.existsById(institucionId)) throw error(404, "Institución no encontrada");
        autorizacion.exigirMiembro(usuarioId, institucionId);
        return retos.findByInstitucion_Id(institucionId, paginas).stream()
                .map(r -> respuesta(r, usuarioId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CampanaRespuestaDTO detalle(Long institucionId, Integer campanaId, Usuario usuario) {
        Long usuarioId = identidad(usuario);
        validarId(institucionId);
        if (campanaId == null || campanaId <= 0) throw error(400, "El ID de campaña debe ser positivo");
        if (!instituciones.existsById(institucionId)) throw error(404, "Institución no encontrada");
        autorizacion.exigirMiembro(usuarioId, institucionId);
        Reto reto = retos.findByIdAndInstitucion_Id(campanaId, institucionId)
                .orElseThrow(() -> error(404, "Campaña no encontrada en esta institución"));
        return respuesta(reto, usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampanaRespuestaDTO> misCampanas(Usuario usuario, int pagina, int tamano) {
        Long usuarioId = identidad(usuario);
        return retos.buscarCampanasVinculadas(usuarioId, pagina(pagina, tamano)).stream()
                .map(r -> respuesta(r, usuarioId)).toList();
    }

    private CampanaRespuestaDTO respuesta(Reto reto, Long usuarioId) {
        LocalDate hoy = LocalDate.now();
        boolean publicada = Boolean.TRUE.equals(reto.getActivo());
        String estado = CalculoSeguimientoColectivo.estadoCampana(new CalculoSeguimientoColectivo.Datos(
                reto.getId(), reto.getTitulo(), reto.getFechaInicio(), reto.getFechaFin(), reto.getActivo()), hoy);
        return new CampanaRespuestaDTO(reto.getId(), reto.getInstitucion().getId(), reto.getTitulo(),
                reto.getDescripcion(), reto.getFechaInicio(), reto.getFechaFin(),
                ChronoUnit.DAYS.between(reto.getFechaInicio(), reto.getFechaFin()) + 1,
                publicada, estado, colectivos.obtener(reto.getId(), usuarioId));
    }

    private void validarDatos(CampanaCrearDTO datos) {
        if (datos == null || datos.nombre() == null || datos.nombre().isBlank() || datos.nombre().length() > 255
                || datos.objetivo() == null || datos.objetivo().isBlank() || datos.objetivo().length() > 255
                || datos.fechaInicio() == null || datos.fechaFin() == null) {
            throw error(400, "Nombre, objetivo (máximo 255 caracteres) y fechas son obligatorios");
        }
        if (datos.fechaInicio().isBefore(LocalDate.now()) || datos.fechaFin().isBefore(datos.fechaInicio())
                || datos.fechaInicio().getYear() > 9999 || datos.fechaFin().getYear() > 9999) {
            throw error(400, "El inicio no puede ser pasado y el fin debe ser igual o posterior al inicio, hasta el año 9999");
        }
    }

    private Pageable pagina(int numero, int tamano) {
        if (numero < 0 || tamano < 1 || tamano > 50) throw error(400, "pagina >= 0 y tamano entre 1 y 50");
        return PageRequest.of(numero, tamano, Sort.by(Sort.Direction.DESC, "fechaInicio", "id"));
    }

    private Long identidad(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) throw error(401, "Se requiere autenticación");
        return usuario.getId();
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) throw error(400, "El ID de institución debe ser positivo");
    }

    private ResponseStatusException error(int estado, String mensaje) {
        return new ResponseStatusException(HttpStatus.valueOf(estado), mensaje);
    }
}
