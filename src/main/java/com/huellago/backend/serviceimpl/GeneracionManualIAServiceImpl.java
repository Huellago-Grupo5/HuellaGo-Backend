package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static com.huellago.backend.dtos.ResultadoGeneracionManualIADTO.Causa.INFORMACION_AMBIENTAL_INSUFICIENTE;
import static com.huellago.backend.dtos.ResultadoGeneracionManualIADTO.Causa.CONTEXTO_PARCIAL;
import static com.huellago.backend.dtos.ResultadoGeneracionManualIADTO.Causa.SIN_ACCIONES_COMPATIBLES;
import static com.huellago.backend.dtos.ResultadoGeneracionManualIADTO.Causa.ACCIONES_YA_REALIZADAS;
import static com.huellago.backend.dtos.ResultadoGeneracionManualIADTO.Causa.SIN_AUTORIZACION_EDITORIAL;
import static com.huellago.backend.dtos.ResultadoGeneracionManualIADTO.Causa.CATEGORIAS_FUERA_DE_ALCANCE;
import static com.huellago.backend.dtos.ResultadoGeneracionManualIADTO.Causa.ABSTENCION_REAL_MODELO;
import static com.huellago.backend.dtos.ResultadoGeneracionManualIADTO.Estado.*;
import static com.huellago.backend.dtos.ResultadoPresentacionRecomendacionesIADTO.EstadoDetalle.*;

/** Sin @Service ni @Transactional: composicion explicita con captura/escritor por proxy Spring.
 * La operacion de publicacion permanece cerrada; no acepta permisos, usuarioId o acciones desde el cliente.
 * Las dependencias editoriales son del servidor; el escritor vuelve a comprobarlas bajo bloqueo. */
public final class GeneracionManualIAServiceImpl implements GeneracionManualIAService {
    private final CapturaContextoAmbientalIAService capturas;
    private final PoliticaAutorizacionEditorialIAService editorial;
    private final OrquestadorRecomendacionesIAService orquestador;
    private final PersistenciaRecomendacionesIAService escritor;
    private final CatalogoAccionesAmbientalesService catalogo;

    public GeneracionManualIAServiceImpl(CapturaContextoAmbientalIAService capturas,
            PoliticaAutorizacionEditorialIAService editorial, OrquestadorRecomendacionesIAService orquestador,
            PersistenciaRecomendacionesIAService escritor) {
        this(capturas, editorial, orquestador, escritor, new CatalogoAccionesAmbientalesServiceImpl());
    }

    public GeneracionManualIAServiceImpl(CapturaContextoAmbientalIAService capturas,
            PoliticaAutorizacionEditorialIAService editorial, OrquestadorRecomendacionesIAService orquestador,
            PersistenciaRecomendacionesIAService escritor, CatalogoAccionesAmbientalesService catalogo) {
        this.capturas=Objects.requireNonNull(capturas); this.editorial=Objects.requireNonNull(editorial);
        this.orquestador=Objects.requireNonNull(orquestador); this.escritor=Objects.requireNonNull(escritor);
        this.catalogo=Objects.requireNonNull(catalogo);
    }

    @Override public ResultadoGeneracionManualIADTO generarParaPruebaInterna(Authentication autenticacion) {
        return generar(autenticacion,PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA);
    }

    @Override public ResultadoGeneracionManualIADTO generarParaUsoUniversitarioLocal(Authentication autenticacion) {
        return generar(autenticacion,PersistenciaRecomendacionesIAService.Destino.USO_UNIVERSITARIO_LOCAL);
    }

    private ResultadoGeneracionManualIADTO generar(Authentication autenticacion,PersistenciaRecomendacionesIAService.Destino destino) {
        fueraDeTransaccion();
        Long usuario=identidad(autenticacion);
        var captura=capturas.capturar(autenticacion);
        if(captura==null || !usuario.equals(captura.usuarioId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"CAPTURA_AJENA");
        var contexto=captura.contexto();
        if(contexto.categoriasValidas().isEmpty())
            return new ResultadoGeneracionManualIADTO(SIN_PROPUESTA,null,null,null,
                    new ResultadoGeneracionManualIADTO.Diagnostico(contexto,Set.of(INFORMACION_AMBIENTAL_INSUFICIENTE),List.of()));
        var elegibilidad=editorial.evaluar(captura.contexto().contexto(),captura.realizadas());
        var diagnostico=diagnosticar(captura,elegibilidad);
        if(elegibilidad.elegibles().isEmpty())
            return new ResultadoGeneracionManualIADTO(SIN_PROPUESTA,elegibilidad,null,null,diagnostico);
        // Tomada antes de inferir; el escritor detectara cambios editoriales al confirmar.
        String receta=escritor.referenciaEditorial(autenticacion,captura,destino);
        fueraDeTransaccion();
        var seleccion=orquestador.orquestarInterno(captura.contexto(),elegibilidad.elegibles(),captura.realizadas());
        var presentacion=seleccion.presentacion();
        if(presentacion.estado()==ResultadoPresentacionRecomendacionesIADTO.Estado.SELECCION_RECHAZADA)
            return new ResultadoGeneracionManualIADTO(SELECCION_RECHAZADA,elegibilidad,seleccion,null,diagnostico.con(ResultadoGeneracionManualIADTO.Causa.SELECCION_RECHAZADA));
        if(presentacion.estado()==ResultadoPresentacionRecomendacionesIADTO.Estado.SIN_PROPUESTA)
            return new ResultadoGeneracionManualIADTO(SIN_PROPUESTA,elegibilidad,seleccion,null,diagnostico);
        if(presentacion.resultados().stream().anyMatch(d->d.estado()==ABSTENCION_V2))
            diagnostico=diagnostico.con(ABSTENCION_REAL_MODELO);
        if(presentacion.resultados().stream().anyMatch(d->d.estado()==ResultadoPresentacionRecomendacionesIADTO.EstadoDetalle.SIN_PRESENTACION))
            return new ResultadoGeneracionManualIADTO(ResultadoGeneracionManualIADTO.Estado.SIN_PRESENTACION,elegibilidad,seleccion,null,diagnostico.con(ResultadoGeneracionManualIADTO.Causa.SIN_PRESENTACION));
        if(presentacion.resultados().stream().noneMatch(d->d.estado()==VISTA_PREVIA_BORRADOR))
            return new ResultadoGeneracionManualIADTO(diagnostico.causas().contains(ABSTENCION_REAL_MODELO)?ABSTENCION_MODELO:SIN_PROPUESTA,
                    elegibilidad,seleccion,null,diagnostico);
        fueraDeTransaccion();
        // REQUIRES_NEW del escritor: bloqueo, lectura MANDATORY, editorial e idempotencia atomicos.
        var guardada=escritor.persistir(autenticacion,captura,seleccion,receta,
                destino);
        return new ResultadoGeneracionManualIADTO(destino==PersistenciaRecomendacionesIAService.Destino.PRUEBA_INTERNA
                ? PERSISTIDA_PRUEBA_INTERNA : PERSISTIDA_USO_UNIVERSITARIO_LOCAL,elegibilidad,seleccion,guardada,diagnostico);
    }

    private ResultadoGeneracionManualIADTO.Diagnostico diagnosticar(CapturaContextoAmbientalIADTO captura,
            PoliticaAutorizacionEditorialIAService.Resultado editorial) {
        var causas=EnumSet.noneOf(ResultadoGeneracionManualIADTO.Causa.class);
        var contexto=captura.contexto();
        if(!contexto.categoriasIncompletas().isEmpty()) causas.add(CONTEXTO_PARCIAL);
        // Consultas de catalogo en memoria: diagnostico solamente; no sustituyen el filtro editorial.
        var posibles=catalogo.compatibles(contexto.contexto(),Set.of());
        var pendientes=catalogo.compatibles(contexto.contexto(),captura.realizadas());
        if(posibles.isEmpty()) causas.add(SIN_ACCIONES_COMPATIBLES);
        if(posibles.stream().anyMatch(c->!pendientes.contains(c))) causas.add(ACCIONES_YA_REALIZADAS);
        if(!editorial.excluidas().isEmpty() || (!pendientes.isEmpty() && editorial.elegibles().isEmpty()))
            causas.add(SIN_AUTORIZACION_EDITORIAL);
        var seleccionables=new HashSet<String>();
        editorial.elegibles().forEach(c->seleccionables.add(c.definicion().categoria()));
        var excluidas=contexto.categoriasValidas().stream().filter(c->!seleccionables.contains(c)).toList();
        if(!excluidas.isEmpty()) causas.add(CATEGORIAS_FUERA_DE_ALCANCE);
        return new ResultadoGeneracionManualIADTO.Diagnostico(contexto,causas,excluidas);
    }

    @Override public ResultadoGeneracionManualIADTO generarParaPublicacionAutorizada(Authentication autenticacion) {
        fueraDeTransaccion();
        identidad(autenticacion);
        // El registro actual acredita definiciones, pero no una aprobacion con finalidad PUBLICABLE.
        // No consultar ni promover sus permisos de prueba; tampoco capturar, inferir o persistir.
        // Requiere un contrato editorial productivo adicional, no una bandera de activacion.
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,"AUTORIZACION_PUBLICABLE_NO_DISPONIBLE");
    }

    private static Long identidad(Authentication autenticacion) {
        if(autenticacion==null || !autenticacion.isAuthenticated()
                || !(autenticacion.getPrincipal() instanceof UserSecurity p)
                || p.getUser()==null || p.getUser().getId()==null || p.getUser().getId()<=0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"IDENTIDAD_INVALIDA");
        return ((UserSecurity)autenticacion.getPrincipal()).getUser().getId();
    }

    private static void fueraDeTransaccion() {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("La generacion manual IA requiere ejecutarse fuera de una transaccion activa");
    }
}
