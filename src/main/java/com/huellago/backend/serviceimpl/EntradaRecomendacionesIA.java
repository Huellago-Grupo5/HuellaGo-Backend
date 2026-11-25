package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.repositories.UsuarioRecomendacionIARepository;
import com.huellago.backend.services.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Adaptador del flujo IA existente para usuarios autenticados en uso universitario local, no otro escritor/orquestador. Lee instantaneas solo despues del guardado. */
class EntradaRecomendacionesIA implements EntradaDemostracionRecomendacionesIA {
    private final GeneracionManualIAService generacion;
    private final UsuarioRecomendacionIARepository asociaciones;
    private final TransactionTemplate lectura;
    EntradaRecomendacionesIA(GeneracionManualIAService generacion,UsuarioRecomendacionIARepository asociaciones,
            org.springframework.transaction.PlatformTransactionManager tm) {
        this.generacion=generacion; this.asociaciones=asociaciones; lectura=new TransactionTemplate(tm); lectura.setReadOnly(true);
    }
    @Override public ResponseEntity<GeneracionRecomendacionesDTO> generar(Authentication a,HttpServletRequest req) {
        try {
            if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Inferencia dentro de transaccion");
            Long usuario=identidad(a,req);
            if((req.getQueryString()!=null && !req.getQueryString().isEmpty()) || req.getInputStream().read()!=-1)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"NO_SE_ADMITEN_PARAMETROS_NI_BODY");
            var r=ejecutar(a);
            List<RecomendacionPersonalizadaDTO> items=new ArrayList<>();
            List<String> bases=new ArrayList<>();
            if(r.persistencia()!=null) lectura.executeWithoutResult(tx->{
                for(var g:r.persistencia().resultados()) {
                    var p=asociaciones.findByIdAndUsuario_Id(g.asociacionId(),usuario)
                            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"ASOCIACION_NO_ENCONTRADA"));
                    if(p.getUsuario()==null || !usuario.equals(p.getUsuario().getId())
                            || p.getRecomendacionIA()==null || !Objects.equals(p.getRecomendacionIA().getId(),g.recomendacionId())
                            || !Objects.equals(g.accionId(),p.getRecomendacionIA().getAccionId()) || !Objects.equals(g.versionAccion(),p.getRecomendacionIA().getVersionAccion())
                            || !accionPermitida(g.accionId(),g.versionAccion())
                            || !Objects.equals(p.getTituloInstantanea(),g.titulo()) || !Objects.equals(p.getDescripcionInstantanea(),g.descripcion())
                            || !Objects.equals(p.getMotivoInstantanea(),g.motivo()) || p.getRecomendacionIA().getCategoria()==null
                            || p.getPrioridadInstantanea()==null || p.getFechaGeneracion()==null)
                        throw new ResponseStatusException(HttpStatus.CONFLICT,"INSTANTANEA_NO_COINCIDE");
                    var item=new RecomendacionPersonalizadaDTO(p.getId(),p.getRecomendacionIA().getId(),p.getRecomendacionIA().getCategoria().getNombre(),
                            p.getTituloInstantanea(),p.getDescripcionInstantanea(),p.getMotivoInstantanea(),p.getPrioridadInstantanea(),null,p.getEstado(),p.getFechaGeneracion());
                    item.indicarImpactoNoEstimable();
                    item.setAccionId(g.accionId());
                    item.setVersionAccion(g.versionAccion());
                    if(PrioridadAccionAmbientalIA.BASE.equals(p.getBasePriorizacion()))
                        item.setJustificacionPrioridad(PrioridadAccionAmbientalIA.evaluar(g.accionId(),g.versionAccion()).justificacion());
                    items.add(item);
                    bases.add(p.getBasePriorizacion());
                }
            });
            String estado=r.estado().name();
            if(!items.isEmpty()) estado=r.persistencia().resultados().stream().allMatch(ResultadoPersistenciaRecomendacionesIADTO.Guardada::reutilizada)
                    ?"RESULTADO_REUTILIZADO":estadoGuardada();
            var dto=responder(estado); dto.setUsuarioId(usuario); dto.setRecomendaciones(items);
            items.sort(Comparator.comparingInt((RecomendacionPersonalizadaDTO x)->ordenPrioridad(x.getPrioridad()))
                    .thenComparing(RecomendacionPersonalizadaDTO::getCategoria)
                    .thenComparing(RecomendacionPersonalizadaDTO::getUsuarioRecomendacionId));
            if(!bases.isEmpty()) {
                // Una reutilización conserva su base histórica; la nueva rúbrica no reescribe instantáneas.
                var distintas=bases.stream().distinct().toList();
                if(distintas.size()==1) dto.setBasePriorizacion(distintas.getFirst());
                else dto.setBasePriorizacion("BASES_HISTORICAS_MIXTAS");
            }
            diagnostico(dto,r);
            return ResponseEntity.ok(dto);
        } catch(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(responder(codigo(e.getReason()))); }
        catch(OllamaClientException e) {
            int status=switch(e.getTipo()) { case TIEMPO_AGOTADO -> 504; case NO_DISPONIBLE -> 503; case CONTEXTO_INVALIDO -> 400; default -> 502; };
            return ResponseEntity.status(status).body(responder("OLLAMA_"+e.getTipo().name()));
        } catch(DataAccessException|TransactionException e) { return ResponseEntity.status(503).body(responder("PERSISTENCIA_NO_DISPONIBLE")); }
        catch(IllegalStateException|java.io.IOException e) { return ResponseEntity.status(500).body(responder("GENERACION_IA_RECHAZADA")); }
    }

    protected Long identidad(Authentication a,HttpServletRequest req) {
        if(!esLocal(req.getLocalAddr()) || !esLocal(req.getRemoteAddr()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"SOLO_USO_UNIVERSITARIO_LOCAL");
        if(a==null || !a.isAuthenticated() || !(a.getPrincipal() instanceof com.huellago.backend.security.UserSecurity p)
                || p.getUser()==null || p.getUser().getId()==null || p.getUser().getId()<=0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"IDENTIDAD_INVALIDA");
        return ((com.huellago.backend.security.UserSecurity)a.getPrincipal()).getUser().getId();
    }
    static boolean esLocal(String direccion) { return Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(direccion==null?"":direccion); }
    protected ResultadoGeneracionManualIADTO ejecutar(Authentication a) { return generacion.generarParaUsoUniversitarioLocal(a); }
    protected boolean accionPermitida(String id,String version) { return id!=null && !id.isBlank() && version!=null && !version.isBlank(); }
    protected String estadoGuardada() { return "PROPUESTA_GUARDADA_USO_UNIVERSITARIO_LOCAL"; }
    protected GeneracionRecomendacionesDTO responder(String estado) { return respuestaPara(estado,"USO_UNIVERSITARIO_LOCAL"); }

    private static String codigo(String razon) { return razon!=null && razon.matches("[A-Z_]{1,80}")?razon:"SOLICITUD_RECHAZADA"; }
    private static int ordenPrioridad(String prioridad) {
        return switch(prioridad==null?"":prioridad) { case "ALTA" -> 0; case "MEDIA" -> 1; case "BAJA" -> 2; default -> 3; };
    }
    private static void diagnostico(GeneracionRecomendacionesDTO dto,ResultadoGeneracionManualIADTO r) {
        var d=r.diagnostico();
        if(d.contexto()!=null) {
            // Incluye categorias y campos: no deducirlos a partir de un alcance que puede no existir.
            var faltantes=new LinkedHashSet<>(d.contexto().categoriasIncompletas());
            faltantes.addAll(d.contexto().observaciones());
            dto.setDatosFaltantes(List.copyOf(faltantes));
        } else if(r.seleccion()!=null && r.seleccion().alcance()!=null)
            dto.setDatosFaltantes(r.seleccion().alcance().incompletas().stream().sorted().toList());
        var advertencias=new ArrayList<>(dto.getAdvertencias());
        if(d.contexto()!=null && d.contexto().resumenHuella()!=null) {
            var resumen=d.contexto().resumenHuella();
            if(resumen.vigencia()==ContextoRecomendacionesIAService.Vigencia.VIGENTE) {
                dto.setHuellaId(resumen.referencia());
                dto.setFechaCalculoHuella(resumen.fecha());
                dto.setUnidad(resumen.unidad());
                dto.setPeriodo(resumen.periodo());
                if(!resumen.referenciasNumericas().isEmpty())
                    advertencias.add("Componentes numéricos de la huella semanal orientativa, en orden descendente: "
                            + resumen.referenciasNumericas().stream().map(x->x.categoria()+" "
                            + x.kgCo2eSemanal().toPlainString()+" kg CO2e ("
                            + (x.perimetro()==ContextoRecomendacionesIAService.Perimetro.HOGAR_SEGUN_CONTEXTO
                                ? "hogar" : "atribución personal no acreditada")+")")
                            .collect(java.util.stream.Collectors.joining(", "))
                            + ". Este orden describe magnitudes registradas de alcance mixto; no compara impacto personal ni ahorro esperado.");
            }
            advertencias.add(switch(resumen.vigencia()) {
                case VIGENTE -> "La huella registrada es orientativa; no acredita un foco personal dominante ni ahorro ambiental.";
                case INEXISTENTE -> "No hay una huella registrada; las sugerencias se basan en los hábitos disponibles.";
                case OBSOLETA -> "La huella registrada es anterior a cambios de hábitos; no se utilizó como información vigente.";
                case INVALIDA, NO_VERIFICABLE -> "No se pudo verificar una huella utilizable; las sugerencias se basan en los hábitos disponibles.";
            });
            if(resumen.comparabilidad()==ContextoRecomendacionesIAService.Comparabilidad.PERIMETROS_NO_COMPARABLES)
                advertencias.add("Los datos del hogar no se atribuyen automáticamente a una persona ni se comparan como impacto personal.");
        }
        if(d.causas().contains(ResultadoGeneracionManualIADTO.Causa.CONTEXTO_PARCIAL))
            advertencias.add("Puedes completar la información indicada; se conservaron las categorías válidas.");
        if(d.causas().contains(ResultadoGeneracionManualIADTO.Causa.CATEGORIAS_FUERA_DE_ALCANCE))
            advertencias.add("Algunas categorías completas no tienen acciones habilitadas para este uso local.");
        if(d.causas().contains(ResultadoGeneracionManualIADTO.Causa.ACCIONES_YA_REALIZADAS))
            advertencias.add("Se excluyeron acciones que ya realizaste.");
        if(d.causas().contains(ResultadoGeneracionManualIADTO.Causa.SIN_AUTORIZACION_EDITORIAL))
            advertencias.add("Algunas acciones no cuentan con autorización editorial para este uso local.");
        if(d.causas().contains(ResultadoGeneracionManualIADTO.Causa.ABSTENCION_REAL_MODELO)
                && r.estado()!=ResultadoGeneracionManualIADTO.Estado.ABSTENCION_MODELO)
            advertencias.add("El modelo se abstuvo en alguna de las categorías seleccionables.");
        dto.setAdvertencias(List.copyOf(advertencias));
        String mensaje=switch(r.estado()) {
            case PERSISTIDA_PRUEBA_INTERNA, PERSISTIDA_USO_UNIVERSITARIO_LOCAL -> "RESULTADO_REUTILIZADO".equals(dto.getEstado())
                    ? "Se recuperaron tus sugerencias guardadas sin modificar su contenido."
                    : "Se guardaron tus sugerencias para este uso local.";
            case ABSTENCION_MODELO -> "El modelo no propuso una acción para las categorías consultadas.";
            case SELECCION_RECHAZADA -> "La respuesta del modelo no superó la validación. No se guardaron sugerencias.";
            case SIN_PRESENTACION -> "No hay una presentación disponible para la selección. No se guardaron sugerencias.";
            case SIN_PROPUESTA -> {
                if(d.causas().contains(ResultadoGeneracionManualIADTO.Causa.INFORMACION_AMBIENTAL_INSUFICIENTE))
                    yield "Completa la información ambiental indicada para obtener sugerencias.";
                if(d.causas().contains(ResultadoGeneracionManualIADTO.Causa.SIN_ACCIONES_COMPATIBLES))
                    yield "No hay acciones compatibles con la información ambiental disponible.";
                if(d.causas().contains(ResultadoGeneracionManualIADTO.Causa.SIN_AUTORIZACION_EDITORIAL))
                    yield "No hay sugerencias con autorización editorial para este uso local.";
                if(d.causas().contains(ResultadoGeneracionManualIADTO.Causa.ACCIONES_YA_REALIZADAS))
                    yield "Las acciones compatibles ya fueron realizadas; no hay nuevas sugerencias.";
                yield "No hay sugerencias disponibles para este uso local.";
            }
        };
        dto.setMensaje(mensaje+" "+dto.getMensaje());
    }
    static GeneracionRecomendacionesDTO respuestaPara(String estado,String finalidad) {
        var dto=new GeneracionRecomendacionesDTO(); dto.setMetodo(PersistenciaRecomendacionesIAServiceImpl.METODO); dto.setEstado(estado);
        dto.setMensaje(finalidad.equals("USO_UNIVERSITARIO_LOCAL") ? "Uso universitario local. No es publicacion publica ni certificacion ambiental." : "Demostracion academica local con datos sinteticos. No es publicacion ni certificacion ambiental.");
        if(estado.startsWith("OLLAMA_")) dto.setMensaje((estado.equals("OLLAMA_RESPUESTA_INVALIDA")
                ? "La respuesta del modelo no es válida. No se guardaron sugerencias. "
                : "No se pudo obtener una respuesta válida del servicio de generación. No se guardaron sugerencias. ")+dto.getMensaje());
        if(estado.equals("CATEGORIA_PENDIENTE_OTRO_CONTEXTO"))
            dto.setMensaje("Ya existe una recomendación IA pendiente en esta categoría para otro contexto ambiental. No se guardó una nueva. "+dto.getMensaje());
        dto.setBasePriorizacion("HABITOS_REGISTRADOS"); dto.setDatosFaltantes(List.of()); dto.setRecomendaciones(List.of()); dto.setAdvertencias(List.of(finalidad.equals("USO_UNIVERSITARIO_LOCAL")?"USO_LOCAL_NO_PUBLICABLE":"EXCLUSIVAMENTE_PRUEBA_INTERNA"));
        dto.setFinalidad(finalidad); dto.setEstadoEditorial("BORRADOR"); dto.setRevisionHumana("PENDIENTE_REVISION"); dto.setPublicable(false);
        return dto;
    }
}
