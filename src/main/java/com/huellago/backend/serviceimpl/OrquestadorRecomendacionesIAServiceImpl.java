package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

/** Sin registro Spring automático, transacciones, repositorios, escritura o fallback.
 * Separa la operacion sintetica de la reutilizable interna, sin habilitacion publica. */
public final class OrquestadorRecomendacionesIAServiceImpl implements OrquestadorRecomendacionesIAService {
    private final GeneradorRecomendacionesIAService selector;
    private final CatalogoAccionesAmbientalesService catalogo;
    private final PresentacionRecomendacionesIAService presentador;

    public OrquestadorRecomendacionesIAServiceImpl(GeneradorRecomendacionesIAService selector,
            CatalogoAccionesAmbientalesService catalogo, PresentacionRecomendacionesIAService presentador) {
        this.selector = Objects.requireNonNull(selector);
        this.catalogo = Objects.requireNonNull(catalogo);
        this.presentador = Objects.requireNonNull(presentador);
    }

    @Override public Resultado orquestarParaPruebasSinteticas(ContextoRecomendacionesIAService.Resultado resultado,
            List<CatalogoAccionesAmbientalesService.Candidata> candidatas,
            Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        return orquestar(resultado,candidatas,realizadas,true);
    }

    @Override public Resultado orquestarInterno(ContextoRecomendacionesIAService.Resultado resultado,
            List<CatalogoAccionesAmbientalesService.Candidata> candidatas,
            Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        return orquestar(resultado,candidatas,realizadas,false);
    }

    private Resultado orquestar(ContextoRecomendacionesIAService.Resultado resultado,
            List<CatalogoAccionesAmbientalesService.Candidata> candidatas,
            Set<AccionAmbientalAdmisible.Referencia> realizadas, boolean sintetico) {
        comprobarTransaccion();
        if (resultado == null || resultado.contexto() == null
                || !ReglasInterpretacionAmbiental.REGLAS.equals(resultado.reglasInterpretacion())
                || candidatas == null || candidatas.stream().anyMatch(Objects::isNull)
                || realizadas == null || realizadas.stream().anyMatch(Objects::isNull))
            throw ValidadorRecomendacionesIA.entrada();
        var datos = ValidadorRecomendacionesIA.contexto(resultado.contexto());
        for (String categoria : resultado.categoriasIncompletas())
            if (datos.containsKey(categoria)) throw ValidadorRecomendacionesIA.entrada();
        var historial = Set.copyOf(realizadas);
        var recibidas = List.copyOf(candidatas);
        var canonicas = List.copyOf(catalogo.compatibles(resultado.contexto(), historial));
        // Contraste completo de los metadatos canónicos; no solo identidad o versión.
        if (new HashSet<>(recibidas).size() != recibidas.size()
                || !canonicas.containsAll(recibidas))
            throw ValidadorRecomendacionesIA.entrada();
        var alcance = new ValidadorRecomendacionFundamentadaIA()
                .construirAlcanceExperimental(resultado.contexto(), recibidas, historial);
        ResultadoGeneracionFundamentadaIADTO seleccion;
        if (recibidas.isEmpty()) {
            seleccion = new ResultadoGeneracionFundamentadaIADTO(null, null,
                    new ResultadoEvaluacionRecomendacionIADTO(
                            ResultadoEvaluacionRecomendacionIADTO.Estado.PENDIENTE_REVISION, List.of(), List.of()));
        } else {
            comprobarTransaccion();
            // Errores tipados se propagan intactos: no son abstenciones ni consejos de respaldo.
            seleccion = sintetico
                    ? selector.generarV2ConAlcanceParaPruebasSinteticas(resultado, recibidas, historial)
                    : selector.generarV2ConAlcanceInterno(resultado, recibidas, historial);
        }
        // El presentador vuelve a validar el JSON y su correspondencia con la propuesta original.
        var presentacion = sintetico
                ? presentador.presentarConAlcanceParaPruebasSinteticas(resultado.contexto(), seleccion, alcance, historial)
                : presentador.presentarConAlcanceInterno(resultado.contexto(), seleccion, alcance, historial);
        Set<String> respondidas = new HashSet<>();
        if (seleccion != null && seleccion.propuesta() != null)
            seleccion.propuesta().resultados().forEach(p -> respondidas.add(p.categoria()));
        return new Resultado(seleccion, presentacion, alcance, respondidas);
    }

    private static void comprobarTransaccion() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("La orquestación IA requiere ejecutarse fuera de una transacción activa");
    }
}
