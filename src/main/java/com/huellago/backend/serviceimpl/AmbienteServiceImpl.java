package com.huellago.backend.serviceimpl;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.huellago.backend.dtos.AmbienteDTO;
import com.huellago.backend.services.AmbienteService;
import java.io.IOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AmbienteServiceImpl implements AmbienteService {
    private static final Logger LOG = LoggerFactory.getLogger(AmbienteServiceImpl.class);
    private static final int MAX_RESPUESTA = 256_000;
    private static final int MAX_RESPUESTA_GEOAPIFY = 512_000;
    private static final int MAX_CACHE = 100;
    private static final long CACHE_SEGUNDOS = 600;
    private final RestClient meteo;
    private final RestClient geoapify;
    private final JsonMapper json;
    private final URI urlMeteo;
    private final URI urlGeoapify;
    private final boolean meteoActivo;
    private final boolean geoapifyActivo;
    private final String geoapifyClave;
    private final int limiteDiarioGeoapify;
    private final Object geoapifyBloqueo = new Object();
    private final ArrayDeque<Long> solicitudesGeoapifySegundo = new ArrayDeque<>();
    private LocalDate diaGeoapify = LocalDate.now(ZoneOffset.UTC);
    private int solicitudesGeoapifyDia;
    private final Map<String, EntradaCache> cache = new LinkedHashMap<>();
    private long ventana = System.currentTimeMillis();
    private int consultasVentana;

    public AmbienteServiceImpl(@Qualifier("openMeteoRestClient") RestClient meteo,
            @Qualifier("geoapifyRestClient") RestClient geoapify, JsonMapper json,
            @Value("${huellago.ambiente.open-meteo.url}") URI urlMeteo,
            @Value("${huellago.ambiente.geoapify.url}") URI urlGeoapify,
            @Value("${huellago.ambiente.open-meteo.enabled:true}") boolean meteoActivo,
            @Value("${huellago.ambiente.geoapify.enabled:false}") boolean geoapifyActivo,
            @Value("${huellago.ambiente.geoapify.api-key:}") String geoapifyClave,
            @Value("${huellago.ambiente.geoapify.daily-limit:1000}") int limiteDiarioGeoapify) {
        if (limiteDiarioGeoapify < 1 || limiteDiarioGeoapify > 1000) {
            throw new IllegalArgumentException("Límite diario Geoapify fuera de rango");
        }
        this.meteo = meteo;
        this.geoapify = geoapify;
        this.json = json;
        this.urlMeteo = urlMeteo;
        this.urlGeoapify = urlGeoapify;
        this.meteoActivo = meteoActivo;
        this.geoapifyActivo = geoapifyActivo;
        this.geoapifyClave = geoapifyClave;
        this.limiteDiarioGeoapify = limiteDiarioGeoapify;
    }

    @Override
    public AmbienteDTO.CalidadAire calidadAire(double latitud, double longitud) {
        if (!meteoActivo) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Calidad del aire deshabilitada");
        String clave = "aire:" + latitud + ':' + longitud;
        Object anterior = consultarCache(clave);
        if (anterior instanceof AmbienteDTO.CalidadAire dato) return dato;
        permitirConsulta();
        byte[] cuerpo = descargar(meteo.get().uri(urlMeteo.toString() + "?latitude=" + latitud
                + "&longitude=" + longitud + "&current=pm2_5,pm10,us_aqi,nitrogen_dioxide&timezone=GMT")
                .accept(MediaType.APPLICATION_JSON));
        JsonNode raiz = analizar(cuerpo);
        JsonNode actual = raiz.path("current");
        JsonNode unidades = raiz.path("current_units");
        String fecha = texto(actual, "time");
        if (!fecha.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?")) invalida();
        for (String campo : List.of("pm2_5", "pm10", "nitrogen_dioxide")) {
            String unidad = texto(unidades, campo);
            if (!"µg/m³".equals(unidad) && !"μg/m³".equals(unidad)) invalida();
        }
        double pm25 = numero(actual, "pm2_5");
        double pm10 = numero(actual, "pm10");
        double no2 = numero(actual, "nitrogen_dioxide");
        double aqi = numero(actual, "us_aqi");
        if (aqi != Math.rint(aqi) || aqi > Integer.MAX_VALUE) invalida();
        AmbienteDTO.CalidadAire resultado = new AmbienteDTO.CalidadAire(numeroCoordenada(raiz,"latitude",-90,90),
                numeroCoordenada(raiz,"longitude",-180,180), fecha + "Z", (int) aqi, pm25, pm10, no2,
                "µg/m³", "Open-Meteo Air Quality API / CAMS",
                "Open-Meteo (https://open-meteo.com/); CAMS ENSEMBLE, Copernicus Atmosphere Monitoring Service (https://atmosphere.copernicus.eu/)",
                "Estimación de un modelo atmosférico, no medición directa de una estación local; no representa emisiones de CO2.");
        guardarCache(clave, resultado);
        return resultado;
    }

    @Override
    public AmbienteDTO.PuntosSostenibles puntosSostenibles(double latitud, double longitud,
            int radioMetros, AmbienteDTO.TipoPunto tipo) {
        if (!geoapifyActivo) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Puntos sostenibles deshabilitados");
        if (geoapifyClave == null || geoapifyClave.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Clave de Geoapify no configurada");
        String clave = "geo:" + latitud + ':' + longitud + ':' + radioMetros + ':' + tipo;
        synchronized (geoapifyBloqueo) {
            Object anterior = consultarCache(clave);
            if (anterior instanceof AmbienteDTO.PuntosSostenibles dato) return dato;
            permitirGeoapify();
            String categoria = switch (tipo) {
                case RECICLAJE -> "service.recycling";
                case ESTACIONAMIENTO_BICICLETA -> "parking.bicycles";
                case ALQUILER_BICICLETA -> "rental.bicycle";
            };
            String coordenadas = longitud + "," + latitud;
            URI destino = UriComponentsBuilder.fromUri(urlGeoapify)
                    .queryParam("categories", categoria)
                    .queryParam("filter", "circle:" + coordenadas + "," + radioMetros)
                    .queryParam("bias", "proximity:" + coordenadas)
                    .queryParam("limit", 20).queryParam("lang", "es")
                    .queryParam("apiKey", geoapifyClave.trim()).build().encode().toUri();
            byte[] cuerpo = descargarGeoapify(geoapify.get().uri(destino).accept(MediaType.APPLICATION_JSON));
            JsonNode raiz = analizar(cuerpo);
            JsonNode elementos = raiz.path("features");
            if (!"FeatureCollection".equals(texto(raiz, "type")) || !elementos.isArray()) invalida();
            List<AmbienteDTO.Punto> encontrados = new ArrayList<>();
            for (JsonNode elemento : elementos) {
                JsonNode propiedades = elemento.path("properties");
                String placeId = opcional(propiedades, "place_id");
                if (placeId == null) continue;
                JsonNode geometria = elemento.path("geometry").path("coordinates");
                double lat = propiedades.path("lat").isNumber()
                        ? numeroCoordenada(propiedades, "lat", -90, 90)
                        : coordenadaGeoJson(geometria, 1, -90, 90);
                double lon = propiedades.path("lon").isNumber()
                        ? numeroCoordenada(propiedades, "lon", -180, 180)
                        : coordenadaGeoJson(geometria, 0, -180, 180);
                JsonNode distanciaDato = propiedades.path("distance");
                double metros = distanciaDato.isNumber() ? numero(propiedades, "distance")
                        : distancia(latitud, longitud, lat, lon);
                JsonNode raw = propiedades.path("datasource").path("raw");
                encontrados.add(new AmbienteDTO.Punto(osmId(raw), opcional(propiedades, "name"),
                        tipo, lat, lon, metros, placeId, opcional(propiedades, "formatted"),
                        primero(opcional(propiedades, "operator"), opcional(raw, "operator")),
                        primero(opcional(propiedades, "opening_hours"), opcional(raw, "opening_hours")),
                        materiales(propiedades.path("recycling_options")), capacidad(raw.path("capacity")),
                        valorSiNo(raw.path("covered")), gratuito(raw.path("fee")),
                        primero(opcional(propiedades.path("restrictions"), "access"), opcional(raw, "access"))));
            }
            List<AmbienteDTO.Punto> puntos = encontrados.stream()
                    .sorted(Comparator.comparingDouble(AmbienteDTO.Punto::distanciaMetros)
                            .thenComparing(AmbienteDTO.Punto::placeId)).limit(20).toList();
            var resultado = new AmbienteDTO.PuntosSostenibles(latitud, longitud, radioMetros, tipo, puntos,
                    "Geoapify Places API / OpenStreetMap",
                    "Powered by Geoapify (https://www.geoapify.com/); © OpenStreetMap contributors, ODbL (https://www.openstreetmap.org/copyright)",
                    "Datos comunitarios potencialmente incompletos; capacidad instalada no equivale a disponibilidad en tiempo real.");
            guardarCache(clave, resultado);
            return resultado;
        }
    }

    private byte[] descargar(RestClient.RequestHeadersSpec<?> solicitud) {
        try {
            return solicitud.exchange((peticion, respuesta) -> {
                if (!respuesta.getStatusCode().is2xxSuccessful()) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Proveedor ambiental no disponible");
                }
                byte[] datos = respuesta.getBody().readNBytes(MAX_RESPUESTA + 1);
                if (datos.length > MAX_RESPUESTA) invalida();
                return datos;
            });
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Proveedor ambiental no disponible", e);
        }
    }

    private byte[] descargarGeoapify(RestClient.RequestHeadersSpec<?> solicitud) {
        try {
            return solicitud.exchange((peticion, respuesta) -> {
                int estado = respuesta.getStatusCode().value();
                if (!respuesta.getStatusCode().is2xxSuccessful()) {
                    LOG.warn("Geoapify devolvió HTTP {}", estado);
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Geoapify devolvió HTTP " + estado);
                }
                byte[] datos = respuesta.getBody().readNBytes(MAX_RESPUESTA_GEOAPIFY + 1);
                if (datos.length > MAX_RESPUESTA_GEOAPIFY) invalida();
                return datos;
            });
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientException e) {
            if (esTimeout(e)) {
                LOG.warn("Geoapify agotó el tiempo de espera");
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Tiempo de espera de Geoapify agotado");
            }
            LOG.warn("No se pudo conectar con Geoapify: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con Geoapify");
        }
    }

    private void permitirGeoapify() {
        LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
        if (!hoy.equals(diaGeoapify)) {
            diaGeoapify = hoy;
            solicitudesGeoapifyDia = 0;
            solicitudesGeoapifySegundo.clear();
        }
        long ahora = System.currentTimeMillis();
        while (!solicitudesGeoapifySegundo.isEmpty()
                && ahora - solicitudesGeoapifySegundo.peekFirst() >= 1000) {
            solicitudesGeoapifySegundo.removeFirst();
        }
        if (solicitudesGeoapifySegundo.size() >= 2 || solicitudesGeoapifyDia >= limiteDiarioGeoapify) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Límite de consultas Geoapify alcanzado");
        }
        solicitudesGeoapifySegundo.addLast(ahora);
        solicitudesGeoapifyDia++;
    }

    private String opcional(JsonNode objeto, String campo) {
        JsonNode dato = objeto.path(campo);
        return dato.isTextual() && !dato.asString().isBlank() ? dato.asString() : null;
    }

    private String primero(String principal, String alternativo) {
        return principal != null ? principal : alternativo;
    }

    private double coordenadaGeoJson(JsonNode coordenadas, int indice, double min, double max) {
        if (!coordenadas.isArray() || coordenadas.size() <= indice) invalida();
        JsonNode dato = coordenadas.get(indice);
        if (!dato.isNumber() || !Double.isFinite(dato.doubleValue())
                || dato.doubleValue() < min || dato.doubleValue() > max) invalida();
        return dato.doubleValue();
    }

    private String osmId(JsonNode raw) {
        JsonNode id = raw.path("osm_id");
        if (!id.isIntegralNumber() && !id.isTextual()) return null;
        String valor = id.asString();
        String tipo = opcional(raw, "osm_type");
        if (tipo == null) return valor;
        String clase = switch (tipo.toLowerCase()) {
            case "n", "node" -> "node";
            case "w", "way" -> "way";
            case "r", "relation" -> "relation";
            default -> tipo.toLowerCase();
        };
        return clase + "/" + valor;
    }

    private List<String> materiales(JsonNode opciones) {
        if (opciones.isMissingNode() || opciones.isNull()) return null;
        if (!opciones.isObject()) invalida();
        List<String> aceptados = new ArrayList<>();
        opciones.properties().forEach(propiedad -> {
            if (propiedad.getValue().isBoolean() && propiedad.getValue().booleanValue())
                aceptados.add(propiedad.getKey());
        });
        aceptados.sort(String::compareTo);
        return List.copyOf(aceptados);
    }

    private Integer capacidad(JsonNode dato) {
        if (dato.isIntegralNumber() && dato.longValue() >= 0
                && dato.longValue() <= Integer.MAX_VALUE) return dato.intValue();
        if (dato.isTextual() && dato.asString().matches("\\d{1,8}"))
            return Integer.parseInt(dato.asString());
        return null;
    }

    private Boolean valorSiNo(JsonNode dato) {
        if (dato.isBoolean()) return dato.booleanValue();
        if (!dato.isTextual()) return null;
        return switch (dato.asString().toLowerCase()) {
            case "yes", "true" -> true;
            case "no", "false" -> false;
            default -> null;
        };
    }

    private Boolean gratuito(JsonNode tarifa) {
        Boolean cobra = valorSiNo(tarifa);
        return cobra == null ? null : !cobra;
    }

    private boolean esTimeout(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SocketTimeoutException || causa instanceof HttpTimeoutException) return true;
        }
        return false;
    }

    private JsonNode analizar(byte[] datos) {
        try {
            JsonNode raiz = json.readTree(datos);
            if (raiz == null || !raiz.isObject()) invalida();
            return raiz;
        }
        catch (JacksonException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respuesta ambiental inválida", e); }
    }

    private String texto(JsonNode objeto, String campo) {
        JsonNode dato = objeto.path(campo);
        if (!dato.isTextual() || dato.asString().isBlank()) { invalida(); }
        return dato.asString();
    }

    private double numero(JsonNode objeto, String campo) {
        JsonNode dato = objeto.path(campo);
        if (!dato.isNumber() || !Double.isFinite(dato.doubleValue()) || dato.doubleValue() < 0) invalida();
        return dato.doubleValue();
    }

    private double numeroCoordenada(JsonNode objeto, String campo, double min, double max) {
        JsonNode dato = objeto.path(campo);
        if (!dato.isNumber() || !Double.isFinite(dato.doubleValue())
                || dato.doubleValue() < min || dato.doubleValue() > max) invalida();
        return dato.doubleValue();
    }

    private double distancia(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(Math.toRadians(lon2 - lon1) / 2), 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void invalida() { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respuesta ambiental inválida"); }

    private synchronized void permitirConsulta() {
        long ahora = System.currentTimeMillis();
        if (ahora - ventana >= 60_000) { ventana = ahora; consultasVentana = 0; }
        if (consultasVentana >= 20) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Límite temporal de consultas ambientales alcanzado");
        consultasVentana++;
    }

    private synchronized Object consultarCache(String clave) {
        EntradaCache dato = cache.get(clave);
        if (dato == null) return null;
        if (Instant.now().getEpochSecond() - dato.creado() > CACHE_SEGUNDOS) {
            cache.remove(clave); return null;
        }
        return dato.valor();
    }

    private synchronized void guardarCache(String clave, Object valor) {
        if (cache.size() >= MAX_CACHE) cache.remove(cache.keySet().iterator().next());
        cache.put(clave, new EntradaCache(valor, Instant.now().getEpochSecond()));
    }

    private record EntradaCache(Object valor, long creado) { }
}
