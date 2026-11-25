package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.CapturaContextoAmbientalIADTO;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.*;
import com.huellago.backend.services.AccionAmbientalAdmisible.Referencia;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Capturas independientes sin bloqueos de escritura ni inferencia.
 * REPEATABLE_READ evita mezclar consultas de distintos snapshots cuando se usa el proxy Spring.
 * No cierra la carrera entre revalidar y guardar; ese control pertenece a la persistencia futura. */
@Service
public class CapturaContextoAmbientalIAServiceImpl implements CapturaContextoAmbientalIAService {
    private static final String VERSION = "HUELLAGO_CONTEXTO_VIGENCIA_V2";
    private final UsuarioRepository usuarios;
    private final UsuarioRecomendacionIARepository recomendaciones;
    private final UsuarioRetoRepository retos;
    private final ContextoRecomendacionesIAService constructor;
    private final JsonMapper mapper = JsonMapper.builder().build();
    public CapturaContextoAmbientalIAServiceImpl(UsuarioRepository usuarios,
            UsuarioRecomendacionIARepository recomendaciones, UsuarioRetoRepository retos,
            ContextoRecomendacionesIAService constructor) {
        this.usuarios = usuarios; this.recomendaciones = recomendaciones; this.retos = retos; this.constructor = constructor;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public CapturaContextoAmbientalIADTO capturar(Authentication autenticacion) {
        return leer(identidad(autenticacion));
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public Revalidacion revalidar(Authentication autenticacion, CapturaContextoAmbientalIADTO original) {
        return comparar(autenticacion, original);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Revalidacion comprobarVigenciaEnTransaccion(Authentication autenticacion, CapturaContextoAmbientalIADTO original) {
        return comparar(autenticacion, original);
    }

    private Revalidacion comparar(Authentication autenticacion, CapturaContextoAmbientalIADTO original) {
        Long id = identidad(autenticacion);
        if (original == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Falta la captura original");
        if (!id.equals(original.usuarioId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"La captura no pertenece al usuario autenticado");
        if (!VERSION.equals(original.versionHuella()) || original.contexto() == null
                || !Objects.equals(original.huellaVigencia(), huella(id, original.contexto(), original.realizadas(),
                original.recomendacionesLegadasRealizadas(), original.retosLegadosRealizados())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La captura original no es consistente");
        var actual = leer(id);
        return new Revalidacion(original.huellaVigencia().equals(actual.huellaVigencia()) ? Estado.VIGENTE : Estado.CAMBIO_DETECTADO,
                actual.huellaVigencia());
    }

    private Long identidad(Authentication a) {
        if (a == null || !a.isAuthenticated() || !(a.getPrincipal() instanceof UserSecurity p)
                || p.getUser() == null || p.getUser().getId() == null || p.getUser().getId() <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Se requiere identidad autenticada");
        return ((UserSecurity)a.getPrincipal()).getUser().getId();
    }

    private CapturaContextoAmbientalIADTO leer(Long id) {
        try {
            if (!usuarios.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Usuario no encontrado");
            var contexto = constructor.construir(id); // Reutiliza todas las reglas existentes; no llama métodos sintéticos.
            Set<Referencia> realizadas = new HashSet<>(); Set<Integer> legadoRecomendaciones = new HashSet<>();
            Set<Integer> legadoRetos = new HashSet<>();
            // Estados actuales: completado y pendiente. La fecha también bloquea repetición en el servicio existente.
            for (String estado : List.of("completado", "pendiente")) {
                for (var a : recomendaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(id,estado)) {
                    if (a == null || !propio(a.getUsuario(),id)) continue;
                    if (!"completado".equalsIgnoreCase(a.getEstado()) && a.getFechaCompletado() == null) continue;
                    Referencia ref = referencia(a.getRecomendacionIA());
                    if (ref != null) realizadas.add(ref); else if (a.getId() != null) legadoRecomendaciones.add(a.getId());
                }
            }
            for (var p : retos.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaAceptacionDesc(id,"completado")) {
                if (p == null || !propio(p.getUsuario(),id) || !"completado".equalsIgnoreCase(p.getEstado()) || p.getReto() == null) continue;
                var origen = p.getReto().getRecomendacionOrigen();
                if (origen == null || !propio(origen.getUsuario(),id)) continue;
                Referencia ref = referencia(origen.getRecomendacionIA());
                if (ref != null) realizadas.add(ref); else if (p.getId() != null) legadoRetos.add(p.getId());
            }
            return new CapturaContextoAmbientalIADTO(VERSION,id,contexto,realizadas,legadoRecomendaciones,legadoRetos,
                    huella(id,contexto,realizadas,legadoRecomendaciones,legadoRetos));
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo leer el contexto ambiental",e);
        }
    }
    private boolean propio(Usuario u, Long id) { return u != null && id.equals(u.getId()); }
    private Referencia referencia(RecomendacionIA r) {
        if (r == null || r.getAccionId() == null || r.getAccionId().isBlank()
                || r.getVersionAccion() == null || r.getVersionAccion().isBlank()) return null;
        return new Referencia(r.getAccionId(),r.getVersionAccion()); // No adivinar versiones ni identidad por título.
    }

    private String huella(Long id, ContextoRecomendacionesIAService.Resultado contexto, Set<Referencia> realizadas,
            Set<Integer> legadoRecomendaciones, Set<Integer> legadoRetos) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                texto(out,VERSION); out.writeLong(id);
                nodo(out,mapper.valueToTree(contexto.contexto()));
                resumen(out, contexto.resumenHuella());
                cadenas(out,new TreeSet<>(contexto.categoriasIncompletas()));
                // Las reglas son parte de la entrada de selección, no autorización editorial.
                out.writeInt(contexto.reglasInterpretacion().size());
                for(String regla:contexto.reglasInterpretacion()) texto(out,regla);
                var ordenadas = realizadas.stream().sorted(Comparator.comparing(Referencia::id).thenComparing(Referencia::version)).toList();
                out.writeInt(ordenadas.size()); for(var r:ordenadas) { texto(out,r.id()); texto(out,r.version()); }
                enteros(out,legadoRecomendaciones); enteros(out,legadoRetos);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException e) { throw new IllegalStateException("No se pudo calcular la referencia de vigencia",e); }
    }
    private void resumen(DataOutputStream out, ContextoRecomendacionesIAService.ResumenHuella r) throws IOException {
        out.writeBoolean(r != null);
        if (r == null) return;
        texto(out,"RESUMEN_HUELLA_V1");
        out.writeBoolean(r.referencia() != null); if (r.referencia() != null) out.writeLong(r.referencia());
        texto(out,r.fecha() == null ? "" : r.fecha().toString());
        texto(out,r.totalRegistrado() == null ? "" : r.totalRegistrado().stripTrailingZeros().toString());
        texto(out,r.unidad()); texto(out,r.periodo()); texto(out,r.origen());
        texto(out,r.vigencia().name()); texto(out,r.comparabilidad().name());
        var componentes = new TreeMap<>(r.componentes()); out.writeInt(componentes.size());
        for (var e : componentes.entrySet()) { texto(out,e.getKey()); texto(out,e.getValue().stripTrailingZeros().toString()); }
        var perimetros = new TreeMap<>(r.perimetros()); out.writeInt(perimetros.size());
        for (var e : perimetros.entrySet()) { texto(out,e.getKey()); texto(out,e.getValue().name()); }
        cadenas(out,new TreeSet<>(r.focos()));
        cadenas(out,new TreeSet<>(r.limitaciones().stream().map(Enum::name).toList()));
    }
    private void nodo(DataOutputStream out, JsonNode n) throws IOException {
        if (n.isNull()) { out.writeByte(0); return; }
        if (n.isObject()) {
            out.writeByte(1); Map<String,JsonNode> campos=new TreeMap<>(); n.properties().forEach(e -> campos.put(e.getKey(),e.getValue()));
            out.writeInt(campos.size()); for(var e:campos.entrySet()) { texto(out,e.getKey()); nodo(out,e.getValue()); } return;
        }
        if (n.isString()) { out.writeByte(2); texto(out,n.asString()); return; }
        if (n.isNumber()) { out.writeByte(3); texto(out,new BigDecimal(n.toString()).stripTrailingZeros().toString()); return; }
        if (n.isBoolean()) { out.writeByte(4); out.writeBoolean(n.asBoolean()); return; }
        throw new IllegalArgumentException("Tipo de contexto no admitido");
    }
    private void enteros(DataOutputStream out, Set<Integer> ids) throws IOException {
        var orden=new TreeSet<>(ids); out.writeInt(orden.size()); for(Integer id:orden) out.writeInt(id);
    }
    private void cadenas(DataOutputStream out, Set<String> valores) throws IOException {
        out.writeInt(valores.size()); for(String valor:valores) texto(out,valor);
    }
    private void texto(DataOutputStream out,String valor) throws IOException {
        byte[] bytes=valor.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
}
