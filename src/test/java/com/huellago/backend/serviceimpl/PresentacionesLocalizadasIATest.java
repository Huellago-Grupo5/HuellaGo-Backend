package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.DefinicionPresentacionIADTO;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class PresentacionesLocalizadasIATest {
    private final PresentacionesLocalizadasIA catalogo = new PresentacionesLocalizadasIA();

    @AfterEach void limpiarIdioma() { LocaleContextHolder.resetLocaleContext(); }

    @Test void treintaVariantesConIdentidadYVariablesIgualesEnAmbosIdiomas() {
        var originales = new PresentacionRecomendacionesIAServiceImpl().definicionesParaRevision();
        var ingles = catalogo.definicionesIngles();
        assertEquals(30, originales.size());
        assertEquals(30, ingles.size());
        assertEquals(10, ingles.stream().map(DefinicionPresentacionIADTO::accionId).distinct().count());
        var ids = originales.stream().map(d -> d.accionId() + "|" + d.versionAccion() + "|" + d.enfoque()
                + "|" + d.versionPresentacion() + "|" + d.camposContextoPermitidos()).collect(java.util.stream.Collectors.toSet());
        assertEquals(ids, ingles.stream().map(d -> d.accionId() + "|" + d.versionAccion() + "|" + d.enfoque()
                + "|" + d.versionPresentacion() + "|" + d.camposContextoPermitidos()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(ingles.stream().noneMatch(d -> d.titulo().equals(originales.stream()
                .filter(e -> e.accionId().equals(d.accionId()) && e.enfoque().equals(d.enfoque()))
                .findFirst().orElseThrow().titulo())));
        var huellas = new PoliticaAutorizacionEditorialIAServiceImpl();
        for (var traduccion : ingles) {
            var original = originales.stream().filter(d -> d.accionId().equals(traduccion.accionId())
                    && d.enfoque().equals(traduccion.enfoque())).findFirst().orElseThrow();
            assertNotEquals(huellas.huellaDefinicion(original), huellas.huellaDefinicion(traduccion));
        }
        // La autorización española aprobada sigue comprobando las 30 huellas originales.
        assertEquals(30, new RegistroEditorialUniversitarioLocal(new PresentacionRecomendacionesIAServiceImpl()).consultar().size());
    }

    @Test void instantaneasDeTodasLasVariantesSePresentanSinCambiarIdentidadesNiPersistencia() {
        var valores = Map.of("/transporte/medio", "Auto", "/transporte/kmSemana", "100",
                "/transporte/diasSemana", "5", "/energia/vivienda", "casa", "/energia/personas", "3",
                "/energia/fuente", "electricidad", "/energia/consumoKwhDiario", "2",
                "/alimentacion/tipo", "mixta", "/residuos/plasticos", "alto", "/residuos/reciclas", "no");
        for (var original : new PresentacionRecomendacionesIAServiceImpl().definicionesParaRevision()) {
            String descripcion = aplicar(original.descripcion(), valores), motivo = aplicar(original.motivo(), valores);
            LocaleContextHolder.setLocale(Locale.forLanguageTag("es-419"));
            assertTrue(catalogo.localizar(original.accionId(), original.versionAccion(), original.enfoque(),
                    original.versionPresentacion(), original.titulo(), descripcion, motivo).isEmpty());
            LocaleContextHolder.setLocale(Locale.US);
            var texto = catalogo.localizar(original.accionId(), original.versionAccion(), original.enfoque(),
                    original.versionPresentacion(), original.titulo(), descripcion, motivo).orElseThrow();
            assertFalse(texto.titulo().isBlank());
            assertFalse(texto.descripcion().contains("{/"));
            assertFalse(texto.motivo().contains("{/"));
            assertNotEquals(descripcion, texto.descripcion());
        }
    }

    @Test void instantaneaAlteradaOVersionDesconocidaNoSeTraduce() {
        var d = new PresentacionRecomendacionesIAServiceImpl().definicionesParaRevision().getFirst();
        LocaleContextHolder.setLocale(Locale.US);
        assertTrue(catalogo.localizar(d.accionId(), d.versionAccion(), d.enfoque(), "2",
                d.titulo(), d.descripcion(), d.motivo()).isEmpty());
        assertTrue(catalogo.localizar(d.accionId(), d.versionAccion(), d.enfoque(), d.versionPresentacion(),
                d.titulo(), d.descripcion() + " extra", d.motivo()).isEmpty());
    }

    @Test void valorBooleanoGeneradoPorJavaSePresentaEnInglesSinAlterarLaInstantanea() {
        var d = new PresentacionRecomendacionesIAServiceImpl().definicionesParaRevision().stream()
                .filter(x -> x.accionId().equals("RE_INICIAR_SEPARACION") && x.enfoque().equals("SIN_ENFOQUE"))
                .findFirst().orElseThrow();
        String motivo = d.motivo().replace("{/residuos/reciclas}", "sí")
                .replace("{/residuos/plasticos}", "Alto");
        LocaleContextHolder.setLocale(Locale.US);
        var traducido = catalogo.localizar(d.accionId(), d.versionAccion(), d.enfoque(),
                d.versionPresentacion(), d.titulo(), d.descripcion(), motivo).orElseThrow();
        assertTrue(traducido.motivo().contains("Recycling record: yes"));
        assertTrue(motivo.contains("sí"));
    }

    private static String aplicar(String plantilla, Map<String, String> valores) {
        String salida = plantilla;
        for (var valor : valores.entrySet()) salida = salida.replace("{" + valor.getKey() + "}", valor.getValue());
        return salida;
    }
}
