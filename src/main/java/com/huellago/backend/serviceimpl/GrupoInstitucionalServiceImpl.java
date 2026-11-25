package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.GrupoInstitucionalDTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.AutorizacionInstitucionalService;
import com.huellago.backend.services.GrupoInstitucionalService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GrupoInstitucionalServiceImpl implements GrupoInstitucionalService {
    private final InstitucionRepository instituciones;
    private final GrupoInstitucionalRepository grupos;
    private final InvitacionInstitucionalRepository invitaciones;
    private final UsuarioInstitucionRepository afiliaciones;
    private final UsuarioRepository usuarios;
    private final AutorizacionInstitucionalService autorizacion;
    private final SecureRandom aleatorio = new SecureRandom();

    public GrupoInstitucionalServiceImpl(InstitucionRepository instituciones, GrupoInstitucionalRepository grupos,
            InvitacionInstitucionalRepository invitaciones, UsuarioInstitucionRepository afiliaciones,
            UsuarioRepository usuarios, AutorizacionInstitucionalService autorizacion) {
        this.instituciones = instituciones;
        this.grupos = grupos;
        this.invitaciones = invitaciones;
        this.afiliaciones = afiliaciones;
        this.usuarios = usuarios;
        this.autorizacion = autorizacion;
    }

    @Override @Transactional
    public GrupoInstitucionalDTO.Invitacion invitar(Long administradorId, Long institucionId) {
        autorizacion.exigirAdministrador(administradorId, institucionId);
        Institucion institucion = institucion(institucionId);
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        InvitacionInstitucional invitacion = new InvitacionInstitucional();
        invitacion.setInstitucion(institucion);
        invitacion.setCodigoHash(hash(codigo));
        invitacion.setFechaExpiracion(LocalDateTime.now().plusDays(7));
        invitaciones.saveAndFlush(invitacion);
        return new GrupoInstitucionalDTO.Invitacion(codigo, institucionId, invitacion.getFechaExpiracion());
    }

    @Override @Transactional
    public GrupoInstitucionalDTO.Afiliacion afiliar(Long usuarioId, String codigo) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) throw error(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        if (codigo == null || !codigo.matches("[A-Za-z0-9_-]{43}")) throw error(HttpStatus.BAD_REQUEST, "Código inválido");
        InvitacionInstitucional invitacion = invitaciones.findByCodigoHash(hash(codigo))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Invitación no disponible"));
        if (invitacion.getFechaUso() != null || !invitacion.getFechaExpiracion().isAfter(LocalDateTime.now()))
            throw error(HttpStatus.CONFLICT, "Invitación usada o vencida");
        Long institucionId = invitacion.getInstitucion().getId();
        if (afiliaciones.existsByUsuario_IdAndInstitucion_Id(usuarioId, institucionId))
            throw error(HttpStatus.CONFLICT, "Ya perteneces a esta institución");
        UsuarioInstitucion vinculo = new UsuarioInstitucion();
        vinculo.setUsuario(usuarios.getReferenceById(usuarioId));
        vinculo.setInstitucion(invitacion.getInstitucion());
        vinculo.setRol(RolInstitucional.MIEMBRO);
        vinculo.setFechaAfiliacion(LocalDateTime.now());
        try {
            afiliaciones.saveAndFlush(vinculo);
            invitacion.setFechaUso(LocalDateTime.now());
            invitacion.setUsadaPor(vinculo.getUsuario());
            invitaciones.saveAndFlush(invitacion);
        } catch (DataIntegrityViolationException e) {
            throw error(HttpStatus.CONFLICT, "Afiliación duplicada");
        }
        return new GrupoInstitucionalDTO.Afiliacion(usuarioId, institucionId, null);
    }

    @Override @Transactional
    public GrupoInstitucionalDTO.Grupo crear(Long administradorId, Long institucionId, GrupoInstitucionalDTO.Datos datos) {
        autorizacion.exigirAdministrador(administradorId, institucionId);
        String nombre = nombre(datos);
        if (grupos.existsByInstitucion_IdAndNombreIgnoreCase(institucionId, nombre))
            throw error(HttpStatus.CONFLICT, "Ya existe un grupo con ese nombre");
        GrupoInstitucional grupo = new GrupoInstitucional();
        grupo.setInstitucion(institucion(institucionId));
        grupo.setTipo(datos.tipo());
        grupo.setNombre(nombre);
        try { return respuesta(grupos.saveAndFlush(grupo)); }
        catch (DataIntegrityViolationException e) { throw error(HttpStatus.CONFLICT, "Grupo duplicado"); }
    }

    @Override @Transactional(readOnly = true)
    public List<GrupoInstitucionalDTO.Grupo> listar(Long administradorId, Long institucionId) {
        autorizacion.exigirAdministradorConsulta(administradorId, institucionId);
        institucion(institucionId);
        return grupos.findByInstitucion_IdOrderByIdAsc(institucionId).stream().map(this::respuesta).toList();
    }

    @Override @Transactional
    public GrupoInstitucionalDTO.Grupo actualizar(Long administradorId, Long institucionId, Long grupoId,
            GrupoInstitucionalDTO.Datos datos) {
        autorizacion.exigirAdministrador(administradorId, institucionId);
        GrupoInstitucional grupo = grupo(grupoId, institucionId);
        String nombre = nombre(datos);
        if (grupos.existsByInstitucion_IdAndNombreIgnoreCaseAndIdNot(institucionId, nombre, grupoId))
            throw error(HttpStatus.CONFLICT, "Ya existe un grupo con ese nombre");
        grupo.setTipo(datos.tipo());
        grupo.setNombre(nombre);
        try { return respuesta(grupos.saveAndFlush(grupo)); }
        catch (DataIntegrityViolationException e) { throw error(HttpStatus.CONFLICT, "Grupo duplicado"); }
    }

    @Override @Transactional
    public void eliminar(Long administradorId, Long institucionId, Long grupoId) {
        autorizacion.exigirAdministrador(administradorId, institucionId);
        GrupoInstitucional grupo = grupo(grupoId, institucionId);
        if (afiliaciones.countByGrupo_Id(grupoId) != 0) throw error(HttpStatus.CONFLICT, "El grupo tiene miembros");
        grupos.delete(grupo);
        grupos.flush();
    }

    @Override @Transactional
    public GrupoInstitucionalDTO.Afiliacion asignar(Long administradorId, Long institucionId, Long grupoId, Long miembroId) {
        autorizacion.exigirAdministrador(administradorId, institucionId);
        GrupoInstitucional grupo = grupo(grupoId, institucionId);
        UsuarioInstitucion miembro = miembro(miembroId, institucionId);
        miembro.setGrupo(grupo);
        afiliaciones.saveAndFlush(miembro);
        return new GrupoInstitucionalDTO.Afiliacion(miembroId, institucionId, grupoId);
    }

    @Override @Transactional
    public GrupoInstitucionalDTO.Afiliacion desasignar(Long administradorId, Long institucionId, Long grupoId, Long miembroId) {
        autorizacion.exigirAdministrador(administradorId, institucionId);
        grupo(grupoId, institucionId);
        UsuarioInstitucion miembro = miembro(miembroId, institucionId);
        if (miembro.getGrupo() == null || !grupoId.equals(miembro.getGrupo().getId()))
            throw error(HttpStatus.CONFLICT, "El miembro no pertenece a ese grupo");
        miembro.setGrupo(null);
        afiliaciones.saveAndFlush(miembro);
        return new GrupoInstitucionalDTO.Afiliacion(miembroId, institucionId, null);
    }

    @Override @Transactional(readOnly = true)
    public GrupoInstitucionalDTO.Ranking ranking(Long administradorId, Long institucionId) {
        autorizacion.exigirAdministradorConsulta(administradorId, institucionId);
        institucion(institucionId);
        record Acumulado(long puntos, int miembros) { }
        Map<Long, Acumulado> totales = new HashMap<>();
        for (var fila : afiliaciones.listarMiembrosVisiblesParaRankingGrupos(institucionId)) {
            if (fila.getGrupoId() == null || fila.getEcoPuntos() == null) continue;
            totales.merge(fila.getGrupoId(), new Acumulado(fila.getEcoPuntos(), 1),
                    (a, b) -> new Acumulado(a.puntos() + b.puntos(), a.miembros() + b.miembros()));
        }
        var orden = new ArrayList<>(grupos.findByInstitucion_IdOrderByIdAsc(institucionId));
        orden.sort(Comparator.<GrupoInstitucional>comparingLong(
                g -> totales.getOrDefault(g.getId(), new Acumulado(0, 0)).puntos()).reversed()
                .thenComparing(GrupoInstitucional::getId));
        List<GrupoInstitucionalDTO.FilaRanking> filas = new ArrayList<>();
        for (var grupo : orden) {
            Acumulado total = totales.getOrDefault(grupo.getId(), new Acumulado(0, 0));
            filas.add(new GrupoInstitucionalDTO.FilaRanking(filas.size() + 1, grupo.getId(),
                    grupo.getTipo(), grupo.getNombre(), total.puntos(), total.miembros()));
        }
        return new GrupoInstitucionalDTO.Ranking(institucionId, filas,
                filas.isEmpty() ? "No hay grupos registrados." : "Solo se suman miembros visibles con eco puntos registrados.");
    }

    private UsuarioInstitucion miembro(Long usuarioId, Long institucionId) {
        return afiliaciones.bloquearParaGrupo(usuarioId, institucionId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Miembro no afiliado a esta institución"));
    }
    private GrupoInstitucional grupo(Long grupoId, Long institucionId) {
        return grupos.findByIdAndInstitucion_Id(grupoId, institucionId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Grupo no encontrado en esta institución"));
    }
    private Institucion institucion(Long id) {
        return instituciones.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Institución no encontrada"));
    }
    private String nombre(GrupoInstitucionalDTO.Datos datos) {
        if (datos == null || datos.tipo() == null || datos.nombre() == null || datos.nombre().trim().length() < 2
                || datos.nombre().trim().length() > 100) throw error(HttpStatus.BAD_REQUEST, "Nombre o tipo inválido");
        return datos.nombre().trim();
    }
    private GrupoInstitucionalDTO.Grupo respuesta(GrupoInstitucional grupo) {
        return new GrupoInstitucionalDTO.Grupo(grupo.getId(), grupo.getInstitucion().getId(),
                grupo.getTipo(), grupo.getNombre());
    }
    private ResponseStatusException error(HttpStatus estado, String mensaje) { return new ResponseStatusException(estado, mensaje); }
    private String hash(String texto) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(texto.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
