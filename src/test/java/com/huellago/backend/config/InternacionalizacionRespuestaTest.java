package com.huellago.backend.config;

import com.huellago.backend.dtos.AmbienteDTO;
import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.dtos.NotificacionRespuestaDTO;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class InternacionalizacionRespuestaTest {
    private final RespuestaLocalizadaAdvice consejo = new RespuestaLocalizadaAdvice();

    InternacionalizacionRespuestaTest() {
        var origen = new ResourceBundleMessageSource();
        origen.setBasename("messages");
        origen.setDefaultEncoding("UTF-8");
        ReflectionTestUtils.setField(consejo, "textos", new TextoLocalizado(origen));
    }

    @AfterEach void limpiar() { LocaleContextHolder.resetLocaleContext(); }

    @Test void acceptLanguageAceptaAmbosIdiomasYUsaEspanolPorDefecto() {
        var resolutor = new InternacionalizacionConfiguration().localeResolver();
        var solicitud = new MockHttpServletRequest();
        assertEquals("es-419", resolutor.resolveLocale(solicitud).toLanguageTag());
        for (String cabecera : List.of("es-419", "es", "fr-FR", "en-GB"))
            assertEquals("es-419", idioma(resolutor, cabecera));
        for (String cabecera : List.of("en-US", "en", "fr-FR, en-US;q=0.7"))
            assertEquals("en-US", idioma(resolutor, cabecera));
    }

    private String idioma(org.springframework.web.servlet.LocaleResolver resolutor, String cabecera) {
        var solicitud = new MockHttpServletRequest();
        solicitud.addHeader("Accept-Language", cabecera);
        return resolutor.resolveLocale(solicitud).toLanguageTag();
    }

    @Test void advertenciasAmbientalesSeTraducenSinModificarFuenteNiDatos() {
        LocaleContextHolder.setLocale(InternacionalizacionConfiguration.INGLES);
        var aire = new AmbienteDTO.CalidadAire(-12, -77, "2026-11-25T12:00Z", 56,
                14.6, 49, 12.1, "µg/m³", "Open-Meteo Air Quality API / CAMS",
                "Open-Meteo / CAMS",
                "Estimación de un modelo atmosférico, no medición directa de una estación local; no representa emisiones de CO2.");
        var traducido = (AmbienteDTO.CalidadAire) traducir(aire);
        assertTrue(traducido.advertencia().startsWith("Atmospheric model estimate"));
        assertEquals(aire.fuente(), traducido.fuente());
        assertEquals(aire.pm25(), traducido.pm25());

        var lugar = new AmbienteDTO.Punto(null, "Nombre original", AmbienteDTO.TipoPunto.RECICLAJE,
                -12, -77, 10, "id", null, null, null, null, null, null, null, null);
        var lugares = new AmbienteDTO.PuntosSostenibles(-12, -77, 3000,
                AmbienteDTO.TipoPunto.RECICLAJE, List.of(lugar), "Geoapify / OpenStreetMap",
                "Geoapify / ODbL",
                "Datos comunitarios potencialmente incompletos; capacidad instalada no equivale a disponibilidad en tiempo real.");
        var traducidos = (AmbienteDTO.PuntosSostenibles) traducir(lugares);
        assertTrue(traducidos.advertencia().startsWith("Community data"));
        assertSame(lugar, traducidos.puntos().getFirst());
    }

    @Test void mensajesIaSeTraducenSinCambiarEstadosNiRecomendaciones() {
        LocaleContextHolder.setLocale(InternacionalizacionConfiguration.INGLES);
        var dato = new GeneracionRecomendacionesDTO();
        dato.setEstado("RESULTADO_REUTILIZADO");
        dato.setFinalidad("USO_UNIVERSITARIO_LOCAL");
        dato.setPublicable(false);
        dato.setMensaje("Se recuperaron tus sugerencias guardadas sin modificar su contenido. Uso universitario local. No es publicacion publica ni certificacion ambiental.");
        dato.setAdvertencias(List.of("No hay una huella registrada; las sugerencias se basan en los hábitos disponibles."));
        dato.setRecomendaciones(List.of());
        var traducido = (GeneracionRecomendacionesDTO) traducir(dato);
        assertTrue(traducido.getMensaje().startsWith("Your saved suggestions"));
        assertTrue(traducido.getAdvertencias().getFirst().startsWith("No footprint is recorded"));
        assertEquals("RESULTADO_REUTILIZADO", traducido.getEstado());
        assertEquals("USO_UNIVERSITARIO_LOCAL", traducido.getFinalidad());
        assertFalse(traducido.getPublicable());
    }

    @Test void textoNoRegistradoPermaneceIntacto() {
        LocaleContextHolder.setLocale(InternacionalizacionConfiguration.INGLES);
        var entrada = java.util.Map.of("mensaje", "Texto proporcionado por el usuario");
        assertEquals(entrada, traducir(entrada));
    }

    @Test void mensajesLegadosYExplicacionesAmbientalesSeTraducenSinCambiarValoresTecnicos() {
        LocaleContextHolder.setLocale(InternacionalizacionConfiguration.INGLES);
        assertEquals("Participation not found", ((java.util.Map<?, ?>) traducir(
                java.util.Map.of("mensaje", "Participación no encontrada"))).get("mensaje"));
        assertEquals("Select at least one environmental goal.", ((java.util.Map<?, ?>) traducir(
                java.util.Map.of("mensaje", "Debes seleccionar al menos un objetivo ambiental."))).get("mensaje"));
        assertEquals("Campaign not found in this institution", ((java.util.Map<?, ?>) traducir(
                java.util.Map.of("mensaje", "Campaña no encontrada en esta institución"))).get("mensaje"));
        var recomendacion = new com.huellago.backend.dtos.RecomendacionPersonalizadaDTO();
        recomendacion.setPrioridad("MEDIA");
        recomendacion.indicarImpactoNoEstimable();
        recomendacion.setJustificacionPrioridad("Agrupar o evitar una salida depende de necesidades y recorridos reales aún desconocidos.");
        @SuppressWarnings("unchecked") var resultado = (List<com.huellago.backend.dtos.RecomendacionPersonalizadaDTO>)
                traducir(List.of(recomendacion));
        assertEquals("MEDIA", resultado.getFirst().getPrioridad());
        assertTrue(resultado.getFirst().getExplicacionImpacto().startsWith("There is not enough information"));
        assertTrue(resultado.getFirst().getJustificacionPrioridad().startsWith("Combining or avoiding"));
    }

    @Test void notificacionHistoricaTraduceFormatoSinModificarNombreDelReto() {
        LocaleContextHolder.setLocale(InternacionalizacionConfiguration.INGLES);
        var aviso = new NotificacionRespuestaDTO(7, "RETO_COMPLETADO", "Reto completado",
                "Completaste el reto: Mi ruta al campus. Ganaste 10 eco puntos.",
                false, java.time.LocalDateTime.of(2026, 11, 25, 12, 0));
        @SuppressWarnings("unchecked")
        var lista = (List<NotificacionRespuestaDTO>) traducir(List.of(aviso));
        assertEquals("Challenge completed", lista.getFirst().titulo());
        assertEquals("You completed the challenge: Mi ruta al campus. You earned 10 eco points.",
                lista.getFirst().mensaje());
        assertEquals(aviso.fechaCreacion(), lista.getFirst().fechaCreacion());
        var nombreCoincidente = new NotificacionRespuestaDTO(8, "RETO_COMPLETADO", "Reto completado",
                "Completaste el reto: Reto completado.", false, aviso.fechaCreacion());
        var preservado = (NotificacionRespuestaDTO) traducir(nombreCoincidente);
        assertEquals("You completed the challenge: Reto completado.", preservado.mensaje());
    }

    private Object traducir(Object dato) {
        var solicitud = new MockHttpServletRequest();
        var respuesta = new MockHttpServletResponse();
        return consejo.beforeBodyWrite(dato, null, MediaType.APPLICATION_JSON, null,
                new ServletServerHttpRequest(solicitud), new ServletServerHttpResponse(respuesta));
    }
}
