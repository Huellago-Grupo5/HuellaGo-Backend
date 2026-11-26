package com.huellago.backend.config;

import com.huellago.backend.dtos.AmbienteDTO;
import com.huellago.backend.dtos.ConversionRecomendacionDTO;
import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.dtos.HuellaEquivalenciasDTO;
import com.huellago.backend.dtos.RecuperacionRespuestaDTO;
import com.huellago.backend.dtos.RankingComunitarioDTO;
import com.huellago.backend.dtos.RankingInstitucionalDTO;
import com.huellago.backend.dtos.GrupoInstitucionalDTO;
import com.huellago.backend.dtos.ComparacionPromedioDTO;
import com.huellago.backend.dtos.PublicacionCreadaDTO;
import com.huellago.backend.dtos.RecomendacionPersonalizadaDTO;
import com.huellago.backend.dtos.NotificacionRespuestaDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.context.i18n.LocaleContextHolder;

/** Localiza campos de presentación sin tocar nombres JSON, estados, entidades ni textos aportados por usuarios. */
@ControllerAdvice
public class RespuestaLocalizadaAdvice implements ResponseBodyAdvice<Object> {
    @Autowired(required = false)
    private TextoLocalizado textos;

    @Override
    public boolean supports(MethodParameter retorno, Class<? extends HttpMessageConverter<?>> convertidor) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object cuerpo, MethodParameter retorno, MediaType contenido,
            Class<? extends HttpMessageConverter<?>> convertidor,
            org.springframework.http.server.ServerHttpRequest solicitud,
            org.springframework.http.server.ServerHttpResponse respuesta) {
        if (textos == null) return cuerpo;
        respuesta.getHeaders().add("Vary", "Accept-Language");
        respuesta.getHeaders().setContentLanguage(LocaleContextHolder.getLocale());
        if (cuerpo instanceof Map<?, ?> mapa && mapa.containsKey("mensaje")) {
            Map<Object, Object> localizado = new LinkedHashMap<>(mapa);
            Object mensaje = mapa.get("mensaje");
            if (mensaje instanceof String valor) localizado.put("mensaje", textos.traducir(valor));
            Object errores = mapa.get("errores");
            if (errores instanceof List<?> lista)
                localizado.put("errores", lista.stream().map(e -> e instanceof String s ? textos.traducir(s) : e).toList());
            return localizado;
        }
        if (cuerpo instanceof List<?> lista && !lista.isEmpty()
                && lista.stream().allMatch(NotificacionRespuestaDTO.class::isInstance))
            return lista.stream().map(item -> localizarNotificacion((NotificacionRespuestaDTO) item)).toList();
        if (cuerpo instanceof List<?> lista && !lista.isEmpty()
                && lista.stream().allMatch(RecomendacionPersonalizadaDTO.class::isInstance)) {
            lista.forEach(item -> localizarExplicacion((RecomendacionPersonalizadaDTO) item));
            return lista;
        }
        if (cuerpo instanceof NotificacionRespuestaDTO dato) return localizarNotificacion(dato);
        if (cuerpo instanceof RecuperacionRespuestaDTO dato)
            return new RecuperacionRespuestaDTO(textos.traducir(dato.mensaje()));
        if (cuerpo instanceof ConversionRecomendacionDTO dato)
            return new ConversionRecomendacionDTO(textos.traducir(dato.mensaje()), dato.usuarioRecomendacionId(),
                    dato.retoId(), dato.participacionId(), dato.estado(), dato.fechaAceptacion());
        if (cuerpo instanceof RankingComunitarioDTO dato)
            return new RankingComunitarioDTO(dato.participantes(), dato.miPosicion(), textos.traducir(dato.mensaje()));
        if (cuerpo instanceof RankingInstitucionalDTO dato)
            return new RankingInstitucionalDTO(dato.institucionId(), dato.participantes(),
                    dato.miPosicion(), dato.gruposDisponibles(), textos.traducir(dato.mensaje()));
        if (cuerpo instanceof GrupoInstitucionalDTO.Ranking dato)
            return new GrupoInstitucionalDTO.Ranking(dato.institucionId(), dato.grupos(),
                    textos.traducir(dato.mensaje()));
        if (cuerpo instanceof PublicacionCreadaDTO dato)
            return new PublicacionCreadaDTO(textos.traducir(dato.mensaje()), dato.publicacion());
        if (cuerpo instanceof ComparacionPromedioDTO dato) {
            dato.setMensaje(textos.traducir(dato.getMensaje()));
            return dato;
        }
        if (cuerpo instanceof GeneracionRecomendacionesDTO dato) {
            dato.setMensaje(textos.traducirCompuesto(dato.getMensaje()));
            if (dato.getAdvertencias() != null)
                dato.setAdvertencias(dato.getAdvertencias().stream().map(textos::traducirCompuesto).toList());
            if (dato.getRecomendaciones() != null) dato.getRecomendaciones().forEach(this::localizarExplicacion);
            return dato;
        }
        if (cuerpo instanceof AmbienteDTO.CalidadAire dato)
            return new AmbienteDTO.CalidadAire(dato.latitud(), dato.longitud(), dato.fechaHora(),
                    dato.indiceCalidadAireUs(), dato.pm25(), dato.pm10(), dato.dioxidoNitrogeno(),
                    dato.unidadConcentraciones(), dato.fuente(), dato.atribucion(), textos.traducir(dato.advertencia()));
        if (cuerpo instanceof AmbienteDTO.PuntosSostenibles dato)
            return new AmbienteDTO.PuntosSostenibles(dato.latitud(), dato.longitud(), dato.radioMetros(),
                    dato.tipo(), dato.puntos(), dato.fuente(), dato.atribucion(), textos.traducir(dato.advertencia()));
        if (cuerpo instanceof HuellaEquivalenciasDTO dato) {
            dato.setAdvertencia(textos.traducir(dato.getAdvertencia()));
            dato.setExplicacionArboles(textos.traducir(dato.getExplicacionArboles()));
            dato.setExplicacionBicicletaKm(textos.traducir(dato.getExplicacionBicicletaKm()));
            dato.setExplicacionReciclajeKg(textos.traducir(dato.getExplicacionReciclajeKg()));
        }
        return cuerpo;
    }

    private NotificacionRespuestaDTO localizarNotificacion(NotificacionRespuestaDTO dato) {
        String mensaje = dato.mensaje();
        if (mensaje != null) {
            String prefijo = switch (dato.tipo()) {
                case "RETO_COMPLETADO" -> "Completaste el reto: ";
                case "INSIGNIA" -> "Obtuviste la insignia: ";
                case "NIVEL" -> "Alcanzaste el nivel ";
                case "RECOMENDACION_ALTA" -> "Tienes una nueva recomendación prioritaria de ";
                default -> null;
            };
            if (prefijo != null && mensaje.startsWith(prefijo)) {
                mensaje = textos.traducir(prefijo) + mensaje.substring(prefijo.length());
                if ("RETO_COMPLETADO".equals(dato.tipo())) {
                    var puntos = java.util.regex.Pattern.compile("^(.*)\\. Ganaste ([0-9]+) eco puntos\\.$",
                            java.util.regex.Pattern.DOTALL).matcher(mensaje);
                    if (puntos.matches()) mensaje = puntos.group(1) + textos.traducir(". Ganaste ")
                            + puntos.group(2) + textos.traducir(" eco puntos.");
                }
            }
        }
        return new NotificacionRespuestaDTO(dato.id(), dato.tipo(), textos.traducir(dato.titulo()),
                mensaje, dato.leida(), dato.fechaCreacion());
    }

    private void localizarExplicacion(RecomendacionPersonalizadaDTO dato) {
        dato.setExplicacionImpacto(textos.traducirCompuesto(dato.getExplicacionImpacto()));
        dato.setJustificacionPrioridad(textos.traducir(dato.getJustificacionPrioridad()));
        dato.setSupuestosImpacto(textos.traducirCompuesto(dato.getSupuestosImpacto()));
        dato.setMetodologiaImpacto(textos.traducirCompuesto(dato.getMetodologiaImpacto()));
        dato.setLimitacionesImpacto(textos.traducir(dato.getLimitacionesImpacto()));
    }
}
