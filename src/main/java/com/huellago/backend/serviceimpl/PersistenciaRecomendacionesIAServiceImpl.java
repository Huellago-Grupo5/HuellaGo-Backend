package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

/** Sin @Service: requiere composición explícita por proxy, no habilitación pública.
 * Constructor público cerrado. Las pruebas componen permisos internos, nunca PUBLICABLE.
 * Requiere índices únicos revisados antes de cualquier uso PostgreSQL concurrente. */
public class PersistenciaRecomendacionesIAServiceImpl implements PersistenciaRecomendacionesIAService {
    public static final String METODO = "OLLAMA_SELECCION_PRESENTACION_CONTROLADA";
    private static final String MODELO = "qwen3:4b-instruct";
    private final UsuarioRepository usuarios;
    private final RecomendacionIARepository acciones;
    private final UsuarioRecomendacionIARepository asociaciones;
    private final CategoriaHabitoRepository categorias;
    private final CapturaContextoAmbientalIAService captura;
    private final PoliticaAutorizacionEditorialIAService editorial;
    private final NotificacionService notificaciones;
    private final jakarta.persistence.EntityManager entityManager;
    private final boolean pruebaInternaHabilitada;
    private final boolean usoUniversitarioLocalHabilitado;
    private final CatalogoAccionesAmbientalesService catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    private final PresentacionRecomendacionesIAService presentador = new PresentacionRecomendacionesIAServiceImpl();
    private final ValidadorRecomendacionFundamentadaIA validador = new ValidadorRecomendacionFundamentadaIA();

    public PersistenciaRecomendacionesIAServiceImpl(UsuarioRepository usuarios, RecomendacionIARepository acciones,
            UsuarioRecomendacionIARepository asociaciones, CategoriaHabitoRepository categorias,
            CapturaContextoAmbientalIAService captura, jakarta.persistence.EntityManager entityManager) {
        this(usuarios,acciones,asociaciones,categorias,captura,entityManager,new PoliticaAutorizacionEditorialIAServiceImpl(),false);
    }
    /** Composición confiable de paquete; no es argumento de persistir ni permiso HTTP. */
    PersistenciaRecomendacionesIAServiceImpl(UsuarioRepository usuarios, RecomendacionIARepository acciones,
            UsuarioRecomendacionIARepository asociaciones, CategoriaHabitoRepository categorias,
            CapturaContextoAmbientalIAService captura, jakarta.persistence.EntityManager entityManager, PoliticaAutorizacionEditorialIAService editorial,
            boolean pruebaInternaHabilitada) {
        this(usuarios,acciones,asociaciones,categorias,captura,entityManager,editorial,pruebaInternaHabilitada,false);
    }
    PersistenciaRecomendacionesIAServiceImpl(UsuarioRepository usuarios, RecomendacionIARepository acciones,
            UsuarioRecomendacionIARepository asociaciones, CategoriaHabitoRepository categorias,
            CapturaContextoAmbientalIAService captura, jakarta.persistence.EntityManager entityManager, PoliticaAutorizacionEditorialIAService editorial,
            boolean pruebaInternaHabilitada, boolean usoUniversitarioLocalHabilitado) {
        this(usuarios, acciones, asociaciones, categorias, captura, entityManager, editorial,
                pruebaInternaHabilitada, usoUniversitarioLocalHabilitado, null);
    }
    PersistenciaRecomendacionesIAServiceImpl(UsuarioRepository usuarios, RecomendacionIARepository acciones,
            UsuarioRecomendacionIARepository asociaciones, CategoriaHabitoRepository categorias,
            CapturaContextoAmbientalIAService captura, jakarta.persistence.EntityManager entityManager, PoliticaAutorizacionEditorialIAService editorial,
            boolean pruebaInternaHabilitada, boolean usoUniversitarioLocalHabilitado, NotificacionService notificaciones) {
        this.usuarios=Objects.requireNonNull(usuarios); this.acciones=Objects.requireNonNull(acciones);
        this.asociaciones=Objects.requireNonNull(asociaciones); this.categorias=Objects.requireNonNull(categorias);
        this.captura=Objects.requireNonNull(captura); this.editorial=Objects.requireNonNull(editorial);
        this.pruebaInternaHabilitada=pruebaInternaHabilitada;
        this.usoUniversitarioLocalHabilitado=usoUniversitarioLocalHabilitado;
        this.entityManager=Objects.requireNonNull(entityManager);
        this.notificaciones=notificaciones;
    }
    private Long identidad(Authentication a, CapturaContextoAmbientalIADTO c) {
        if(a==null || !a.isAuthenticated() || !(a.getPrincipal() instanceof UserSecurity p)
                || p.getUser()==null || p.getUser().getId()==null || p.getUser().getId()<=0)
            throw error(HttpStatus.UNAUTHORIZED,"IDENTIDAD_INVALIDA");
        Long id=((UserSecurity)a.getPrincipal()).getUser().getId();
        if(c==null || !id.equals(c.usuarioId())) throw error(HttpStatus.FORBIDDEN,"CAPTURA_AJENA");
        return id;
    }
    private void permiso(Destino destino) {
        // La politica actual no distingue aprobacion productiva de aptitud de prueba.
        // Ni el booleano interno ni una receta de prueba pueden habilitar PUBLICABLE.
        if(destino==Destino.PUBLICABLE)
            throw error(HttpStatus.FORBIDDEN,"AUTORIZACION_PUBLICABLE_NO_DISPONIBLE");
        if(destino==Destino.USO_UNIVERSITARIO_LOCAL && usoUniversitarioLocalHabilitado) return;
        if(destino!=Destino.PRUEBA_INTERNA || !pruebaInternaHabilitada)
            throw error(HttpStatus.FORBIDDEN,"DESTINO_NO_AUTORIZADO");
    }
    private List<CatalogoAccionesAmbientalesService.Candidata> elegibles(CapturaContextoAmbientalIADTO c) {
        var canonicas=catalogo.compatibles(c.contexto().contexto(),c.realizadas());
        var e=editorial.evaluar(c.contexto().contexto(),c.realizadas()).elegibles();
        if(e.isEmpty()) throw error(HttpStatus.FORBIDDEN,"SIN_AUTORIZACION_EDITORIAL");
        if(new HashSet<>(e).size()!=e.size() || !canonicas.containsAll(e))
            throw error(HttpStatus.FORBIDDEN,"REGISTRO_EDITORIAL_INVALIDO");
        return List.copyOf(e);
    }
    @Override public String referenciaEditorial(Authentication a, CapturaContextoAmbientalIADTO c) {
        return referenciaEditorial(a,c,Destino.PRUEBA_INTERNA);
    }
    @Override public String referenciaEditorial(Authentication a, CapturaContextoAmbientalIADTO c,Destino destino) {
        identidad(a,c); permiso(destino);
        return receta(elegibles(c),destino);
    }
    private String receta(List<CatalogoAccionesAmbientalesService.Candidata> permitidas,Destino destino) {
        permiso(destino);
        var definiciones=presentador.definicionesParaRevision();
        List<String> partes=new ArrayList<>(List.of("HUELLAGO_RECETA_INTERNA_V1",
                destino==Destino.USO_UNIVERSITARIO_LOCAL ? "USO_UNIVERSITARIO_LOCAL" : "APTA_PARA_PRUEBA_INTERNA",METODO));
        for(var a:permitidas.stream().sorted(Comparator.comparing(x->x.definicion().id()+"/"+x.definicion().version())).toList()) {
            partes.add(a.definicion().id()); partes.add(a.definicion().version()); partes.add(a.definicion().categoria());
            var familia=definiciones.stream().filter(d->d.accionId().equals(a.definicion().id()) && d.versionAccion().equals(a.definicion().version()))
                    .sorted(Comparator.comparing(DefinicionPresentacionIADTO::enfoque)).toList();
            if(familia.size()!=3) throw error(HttpStatus.FORBIDDEN,"FAMILIA_INCOMPLETA");
            for(var d:familia) { partes.add(d.enfoque()); partes.add(d.versionPresentacion()); partes.add(editorial.huellaDefinicion(d)); }
        }
        return digest(partes);
    }
    private String clave(CapturaContextoAmbientalIADTO c, String categoria, String receta) {
        return "IA_GEN_V1:"+digest(List.of("HUELLAGO_GENERACION_V1",c.versionHuella(),c.usuarioId().toString(),
                c.huellaVigencia(),categoria,receta,METODO));
    }
    private String digest(List<String> partes) {
        try {
            var bytes=new ByteArrayOutputStream();
            try(var out=new DataOutputStream(bytes)) {
                for(String s:partes) { byte[] b=s.getBytes(StandardCharsets.UTF_8); out.writeInt(b.length); out.write(b); }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch(IOException|NoSuchAlgorithmException e) { throw new IllegalStateException("No se pudo calcular la referencia interna",e); }
    }
    @Override
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public ResultadoPersistenciaRecomendacionesIADTO persistir(Authentication a, CapturaContextoAmbientalIADTO c,
            OrquestadorRecomendacionesIAService.Resultado seleccion, String referenciaEditorial, Destino destino) {
        Long id=identidad(a,c); permiso(destino);
        // Defensa frente a composición directa sin proxy o un gestor mal configurado.
        if(!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Objects.equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),java.sql.Connection.TRANSACTION_READ_COMMITTED))
            throw new IllegalStateException("Se requiere proxy con transacción de escritura READ_COMMITTED");
        // Unidad exclusiva REQUIRES_NEW, sin entidades de entrada ni cambios previos del escritor.
        // Descartar caché heredada de lectura (p. ej. OSIV) antes de cualquier consulta/auto-flush.
        // No hacer flush aquí: no deben propagarse cambios ajenos a esta operación.
        entityManager.clear();
        usuarios.bloquearParaRecomendaciones(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"USUARIO_INEXISTENTE"));
        if(captura.comprobarVigenciaEnTransaccion(a,c).estado()!=CapturaContextoAmbientalIAService.Estado.VIGENTE)
            throw error(HttpStatus.CONFLICT,"CONTEXTO_MODIFICADO");
        var permitidas=elegibles(c); String receta=receta(permitidas,destino);
        if(!receta.equals(referenciaEditorial)) throw error(HttpStatus.CONFLICT,"RECETA_EDITORIAL_MODIFICADA");
        var alcance=validador.construirAlcanceExperimental(c.contexto().contexto(),permitidas,c.realizadas());
        if(seleccion==null || !alcance.equals(seleccion.alcance())) throw error(HttpStatus.CONFLICT,"ALCANCE_INVALIDO");
        // Se vuelve a validar JSON original y se recompone exclusivamente el prototipo sintético.
        var verificada=presentador.presentarConAlcanceParaPruebasSinteticas(c.contexto().contexto(),seleccion.seleccion(),alcance,c.realizadas());
        if(verificada.estado()!=ResultadoPresentacionRecomendacionesIADTO.Estado.SELECCION_ACEPTADA
                || !verificada.equals(seleccion.presentacion())) throw error(HttpStatus.CONFLICT,"SELECCION_O_PRESENTACION_INVALIDA");
        List<ResultadoPresentacionRecomendacionesIADTO.Detalle> propuestas=verificada.resultados().stream()
                .filter(d->d.seleccionOriginal().decision()==RecomendacionFundamentadaIADTO.Decision.PROPONER).toList();
        Map<String,CategoriaHabito> porCategoria=new HashMap<>();
        for(var d:propuestas) {
            if(d.estado()!=ResultadoPresentacionRecomendacionesIADTO.EstadoDetalle.VISTA_PREVIA_BORRADOR || d.presentacion()==null)
                throw error(HttpStatus.CONFLICT,"SIN_PRESENTACION");
            var p=d.presentacion(); texto(p.titulo(),160); texto(p.descripcion(),1000); texto(p.motivo(),1000);
            String categoria=d.seleccionOriginal().categoria();
            var entidad=categorias.findByNombreIgnoreCase(categoria);
            if(entidad==null || entidad.getId()==null) throw error(HttpStatus.CONFLICT,"CATEGORIA_INEXISTENTE");
            porCategoria.put(categoria,entidad);
        }
        porCategoria.values().stream().map(CategoriaHabito::getId).distinct().sorted().forEach(cat ->
                categorias.bloquearParaRecomendaciones(cat).orElseThrow(()->error(HttpStatus.CONFLICT,"CATEGORIA_INEXISTENTE")));
        var usuario=usuarios.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"USUARIO_INEXISTENTE"));
        List<ResultadoPersistenciaRecomendacionesIADTO.Guardada> resultado=new ArrayList<>();
        for(var d:propuestas) {
            var p=d.presentacion(); var categoria=porCategoria.get(d.seleccionOriginal().categoria());
            String clave=clave(c,d.seleccionOriginal().categoria(),receta);
            var existentes=asociaciones.findByUsuario_IdAndReferenciaContextoAndMetodoGeneracion(id,clave,METODO);
            if(existentes.size()>1) throw error(HttpStatus.CONFLICT,"GENERACIONES_DUPLICADAS");
            if(!existentes.isEmpty()) {
                var anterior=existentes.getFirst(); verificarExistente(anterior,id,categoria.getId(),clave,permitidas);
                resultado.add(resultado(anterior,true)); continue;
            }
            // HU37 crea pendientes deterministas sin metodoGeneracion. Pueden coexistir con
            // una IA: la exclusión por captura diferente se aplica solo al mismo motor IA.
            if(asociaciones.findByUsuario_IdAndEstadoIgnoreCaseAndRecomendacionIA_Categoria_Id(id,"pendiente",categoria.getId())
                    .stream().anyMatch(pendiente -> pendiente != null && METODO.equals(pendiente.getMetodoGeneracion())))
                throw error(HttpStatus.CONFLICT,"CATEGORIA_PENDIENTE_OTRO_CONTEXTO");
            var canonicas=acciones.findByAccionIdAndVersionAccionOrderByIdAsc(p.accionId(),p.versionAccion());
            if(canonicas.size()>1) throw error(HttpStatus.CONFLICT,"IDENTIDAD_CANONICA_DUPLICADA");
            RecomendacionIA accion;
            if(canonicas.isEmpty()) {
                accion=new RecomendacionIA(); accion.setAccionId(p.accionId()); accion.setVersionAccion(p.versionAccion());
                accion.setCategoria(categoria); accion.setTitulo("Acción IA: "+p.accionId()+" / "+p.versionAccion());
                accion.setDescripcion("Identidad canónica de acción ambiental; contenido individual en la asociación.");
                // La fila canónica compartida no expresa una prioridad personalizada.
                accion.setPrioridad("MEDIA"); accion.setPuntosRecompensa(0); accion=acciones.saveAndFlush(accion);
            } else {
                accion=canonicas.getFirst();
                if(accion.getId()==null || !p.accionId().equals(accion.getAccionId()) || !p.versionAccion().equals(accion.getVersionAccion())
                        || accion.getCategoria()==null || !categoria.getId().equals(accion.getCategoria().getId()))
                    throw error(HttpStatus.CONFLICT,"CATEGORIA_CANONICA_INVALIDA");
            }
            var nueva=new UsuarioRecomendacionIA(); nueva.setUsuario(usuario); nueva.setRecomendacionIA(accion);
            nueva.setEstado("Pendiente"); nueva.setFechaGeneracion(LocalDateTime.now());
            nueva.setTituloInstantanea(p.titulo()); nueva.setDescripcionInstantanea(p.descripcion()); nueva.setMotivoInstantanea(p.motivo());
            nueva.setEnfoque(p.enfoqueSeleccionado()?p.enfoque():null); nueva.setVersionPresentacion(p.versionPresentacion());
            nueva.setMetodoGeneracion(METODO); nueva.setReferenciaContexto(clave); nueva.setModeloGeneracion(MODELO);
            var prioridad=PrioridadAccionAmbientalIA.evaluar(p.accionId(),p.versionAccion());
            nueva.setPrioridadInstantanea(prioridad.prioridad());
            nueva.setBasePriorizacion(PrioridadAccionAmbientalIA.BASE);
            nueva=asociaciones.saveAndFlush(nueva);
            if("ALTA".equals(prioridad.prioridad()) && notificaciones!=null)
                notificaciones.crearPorEvento(usuario,"RECOMENDACION_ALTA:"+nueva.getId(),
                        "Nueva recomendación prioritaria","Tienes una nueva recomendación prioritaria de "
                                +d.seleccionOriginal().categoria()+".","RECOMENDACION_ALTA");
            resultado.add(resultado(nueva,false));
        }
        // Evita confirmar si una fuente editorial mutable cambió durante la operación.
        // No certifica revocación atómica multiinstancia: requiere manifiesto inmutable por despliegue.
        if(!receta.equals(receta(elegibles(c),destino))) throw error(HttpStatus.CONFLICT,"RECETA_EDITORIAL_MODIFICADA");
        return new ResultadoPersistenciaRecomendacionesIADTO(resultado);
    }
    private void verificarExistente(UsuarioRecomendacionIA a, Long usuario, Long categoria, String clave,
            List<CatalogoAccionesAmbientalesService.Candidata> permitidas) {
        var r=a.getRecomendacionIA();
        if(a.getUsuario()==null || !usuario.equals(a.getUsuario().getId()) || r==null || r.getCategoria()==null
                || !categoria.equals(r.getCategoria().getId()) || !clave.equals(a.getReferenciaContexto()) || !METODO.equals(a.getMetodoGeneracion())
                || permitidas.stream().noneMatch(x->x.definicion().id().equals(r.getAccionId()) && x.definicion().version().equals(r.getVersionAccion())))
            throw error(HttpStatus.CONFLICT,"GENERACION_EXISTENTE_INVALIDA");
        texto(a.getTituloInstantanea(),160); texto(a.getDescripcionInstantanea(),1000); texto(a.getMotivoInstantanea(),1000);
        if(a.getVersionPresentacion()==null || a.getFechaGeneracion()==null || a.getId()==null)
            throw error(HttpStatus.CONFLICT,"INSTANTANEA_EXISTENTE_INCOMPLETA");
        String enfoque=a.getEnfoque()==null?"SIN_ENFOQUE":a.getEnfoque();
        if(presentador.definicionesParaRevision().stream().noneMatch(d->d.accionId().equals(r.getAccionId())
                && d.versionAccion().equals(r.getVersionAccion()) && d.enfoque().equals(enfoque)
                && d.versionPresentacion().equals(a.getVersionPresentacion()))
                || !"Pendiente".equalsIgnoreCase(a.getEstado()) || !MODELO.equals(a.getModeloGeneracion())
                || a.getPrioridadInstantanea()==null || a.getBasePriorizacion()==null)
            throw error(HttpStatus.CONFLICT,"GENERACION_EXISTENTE_INVALIDA");
        if(a.getFechaCompletado()!=null || "completado".equalsIgnoreCase(a.getEstado()))
            throw error(HttpStatus.CONFLICT,"ACCION_REALIZADA");
    }
    private ResultadoPersistenciaRecomendacionesIADTO.Guardada resultado(UsuarioRecomendacionIA a,boolean reutilizada) {
        var r=a.getRecomendacionIA();
        return new ResultadoPersistenciaRecomendacionesIADTO.Guardada(a.getId(),r.getId(),reutilizada,r.getAccionId(),r.getVersionAccion(),
                a.getEnfoque(),a.getVersionPresentacion(),a.getTituloInstantanea(),a.getDescripcionInstantanea(),a.getMotivoInstantanea(),a.getEstado(),a.getFechaGeneracion());
    }
    private void texto(String s,int max) { if(s==null || s.isBlank() || s.length()>max) throw error(HttpStatus.CONFLICT,"INSTANTANEA_INCOMPLETA"); }
    private ResponseStatusException error(HttpStatus status,String motivo) { return new ResponseStatusException(status,motivo); }
}
