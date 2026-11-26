package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.AmbienteDTO;
import java.net.URI;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AmbienteServiceImplTest {
    private MockRestServiceServer servidorMeteo;
    private MockRestServiceServer servidorGeoapify;
    private RestClient clienteMeteo;
    private RestClient clienteGeoapify;

    @BeforeEach void preparar() {
        var meteo = RestClient.builder();
        servidorMeteo = MockRestServiceServer.bindTo(meteo).build();
        clienteMeteo = meteo.build();
        var geoapify = RestClient.builder();
        servidorGeoapify = MockRestServiceServer.bindTo(geoapify).build();
        clienteGeoapify = geoapify.build();
    }

    private AmbienteServiceImpl servicio(boolean meteo, boolean geoapify, String clave, int limite) {
        return new AmbienteServiceImpl(clienteMeteo, clienteGeoapify, JsonMapper.builder().build(),
                URI.create("https://air-quality-api.open-meteo.com/v1/air-quality"),
                URI.create("https://api.geoapify.com/v2/places"), meteo, geoapify, clave, limite);
    }

    private AmbienteServiceImpl servicio() { return servicio(false, true, "clave-ficticia", 1000); }

    private String geojson() {
        return """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"place_id":"lejos","lat":-12.05,"lon":-77.05,"distance":650,"datasource":{"raw":{"osm_id":200,"osm_type":"way"}}}},
                  {"type":"Feature","properties":{"place_id":"cerca","name":"Reciclaje","lat":-12.0464,"lon":-77.0428,"distance":0,
                    "operator":"Municipio","opening_hours":"Mo-Fr","recycling_options":{"glass":true,"paper":false,"plastic":true},
                    "datasource":{"raw":{"osm_id":100,"osm_type":"node","capacity":"12","covered":"yes","fee":"no","access":"yes"}}}}]}
                """;
    }

    private void esperar(String categoria, String cuerpo) {
        servidorGeoapify.expect(requestTo(containsString("categories=" + categoria)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(req -> {
                    String uri = req.getURI().toString();
                    assertTrue(uri.contains("filter=circle"));
                    assertTrue(uri.contains("-77.0428"));
                    assertTrue(uri.contains("-12.0464"));
                    assertTrue(uri.contains("bias=proximity"));
                    assertTrue(uri.contains("limit=20"));
                    assertTrue(uri.contains("lang=es"));
                    assertTrue(uri.contains("apiKey=clave-ficticia"));
                }).andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));
    }

    @Test void meteoSigueOperativo() {
        servidorMeteo.expect(requestTo(containsString("current=pm2_5,pm10,us_aqi,nitrogen_dioxide")))
                .andRespond(withSuccess("""
                    {"latitude":-12.05,"longitude":-77.04,
                     "current_units":{"pm2_5":"µg/m³","pm10":"µg/m³","nitrogen_dioxide":"µg/m³"},
                     "current":{"time":"2026-11-25T14:00","pm2_5":8.1,"pm10":15.2,"us_aqi":42,"nitrogen_dioxide":11.3}}
                    """, MediaType.APPLICATION_JSON));
        var servicio = servicio(true, false, "", 1000);
        var dato = servicio.calidadAire(-12, -77);
        assertEquals(42, dato.indiceCalidadAireUs());
        assertSame(dato, servicio.calidadAire(-12, -77));
        servidorMeteo.verify();
    }

    @Test void reciclajeConvierteOpcionalesYOrdena() {
        esperar("service.recycling", geojson());
        var dato = servicio().puntosSostenibles(-12.0464, -77.0428, 3000, AmbienteDTO.TipoPunto.RECICLAJE);
        assertEquals(2, dato.puntos().size());
        var cerca = dato.puntos().get(0);
        assertEquals("cerca", cerca.placeId());
        assertEquals("node/100", cerca.osmId());
        assertEquals("Reciclaje", cerca.nombre());
        assertEquals(java.util.List.of("glass", "plastic"), cerca.materialesAceptados());
        assertEquals(12, cerca.capacidad());
        assertEquals(true, cerca.techado());
        assertEquals(true, cerca.gratuito());
        assertEquals("yes", cerca.acceso());
        assertNull(dato.puntos().get(1).nombre());
        assertNull(dato.puntos().get(1).materialesAceptados());
        servidorGeoapify.verify();
    }

    @Test void categoriasBicicletaYVacio() {
        esperar("parking.bicycles", "{\"type\":\"FeatureCollection\",\"features\":[]}");
        esperar("rental.bicycle", "{\"type\":\"FeatureCollection\",\"features\":[]}");
        var servicio = servicio();
        assertTrue(servicio.puntosSostenibles(-12.0464,-77.0428,3000,
                AmbienteDTO.TipoPunto.ESTACIONAMIENTO_BICICLETA).puntos().isEmpty());
        assertTrue(servicio.puntosSostenibles(-12.0464,-77.0428,3000,
                AmbienteDTO.TipoPunto.ALQUILER_BICICLETA).puntos().isEmpty());
        servidorGeoapify.verify();
    }

    @Test void coordenadasGeoJsonYOpcionalesAusentes() {
        esperar("parking.bicycles", """
                {"type":"FeatureCollection","features":[{"type":"Feature",
                 "geometry":{"type":"Point","coordinates":[-77.0428,-12.0464]},
                 "properties":{"place_id":"sin-metadatos"}}]}
                """);
        var punto = servicio().puntosSostenibles(-12.0464,-77.0428,3000,
                AmbienteDTO.TipoPunto.ESTACIONAMIENTO_BICICLETA).puntos().get(0);
        assertEquals("sin-metadatos", punto.placeId());
        assertEquals(0, punto.distanciaMetros(), 0.01);
        assertNull(punto.osmId());
        assertNull(punto.nombre());
        assertNull(punto.capacidad());
        assertNull(punto.techado());
        assertNull(punto.gratuito());
        assertNull(punto.acceso());
        servidorGeoapify.verify();
    }

    @Test void geoJsonConCoordenadasInvalidasEsError() {
        esperar("service.recycling", """
                {"type":"FeatureCollection","features":[{"type":"Feature",
                 "geometry":{"type":"Point","coordinates":[-200,-12]},
                 "properties":{"place_id":"invalido"}}]}
                """);
        assertEquals(502, assertThrows(ResponseStatusException.class, () ->
                servicio().puntosSostenibles(-12.0464,-77.0428,3000,
                        AmbienteDTO.TipoPunto.RECICLAJE)).getStatusCode().value());
        servidorGeoapify.verify();
    }

    @Test void cacheNoConsumeCuotaDiaria() {
        esperar("service.recycling", geojson());
        var servicio = servicio(false,true,"clave-ficticia",1);
        var primero = servicio.puntosSostenibles(-12.0464,-77.0428,3000,AmbienteDTO.TipoPunto.RECICLAJE);
        assertSame(primero, servicio.puntosSostenibles(-12.0464,-77.0428,3000,AmbienteDTO.TipoPunto.RECICLAJE));
        assertEquals(429, assertThrows(ResponseStatusException.class, () ->
                servicio.puntosSostenibles(-12.0464,-77.0428,3001,AmbienteDTO.TipoPunto.RECICLAJE)).getStatusCode().value());
        servidorGeoapify.verify();
    }

    @Test void limitaDosConsultasPorSegundo() {
        esperar("service.recycling", geojson());
        esperar("service.recycling", geojson());
        var servicio = servicio();
        servicio.puntosSostenibles(-12.0464,-77.0428,3000,AmbienteDTO.TipoPunto.RECICLAJE);
        servicio.puntosSostenibles(-12.0464,-77.0428,3001,AmbienteDTO.TipoPunto.RECICLAJE);
        assertEquals(429, assertThrows(ResponseStatusException.class, () ->
                servicio.puntosSostenibles(-12.0464,-77.0428,3002,AmbienteDTO.TipoPunto.RECICLAJE)).getStatusCode().value());
        servidorGeoapify.verify();
    }

    @Test void desactivadoOSinClaveNoConsulta() {
        assertEquals(503, assertThrows(ResponseStatusException.class, () ->
                servicio(false,false,"",1000).puntosSostenibles(-12,-77,3000,AmbienteDTO.TipoPunto.RECICLAJE)).getStatusCode().value());
        assertEquals(503, assertThrows(ResponseStatusException.class, () ->
                servicio(false,true,"",1000).puntosSostenibles(-12,-77,3000,AmbienteDTO.TipoPunto.RECICLAJE)).getStatusCode().value());
        servidorGeoapify.verify();
    }

    @Test void respuestaInvalidaNoEsListaVacia() {
        esperar("service.recycling", "{\"error\":\"failure\"}");
        assertEquals(502, assertThrows(ResponseStatusException.class, () ->
                servicio().puntosSostenibles(-12.0464,-77.0428,3000,AmbienteDTO.TipoPunto.RECICLAJE)).getStatusCode().value());
    }

    @Test void falloHttpNoFiltraClave() {
        servidorGeoapify.expect(requestTo(containsString("apiKey=clave-ficticia")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        var error = assertThrows(ResponseStatusException.class, () ->
                servicio().puntosSostenibles(-12,-77,3000,AmbienteDTO.TipoPunto.RECICLAJE));
        assertEquals(503, error.getStatusCode().value());
        assertFalse(error.toString().contains("clave-ficticia"));
        servidorGeoapify.verify();
    }

    @Test void timeoutNoFiltraClave() {
        servidorGeoapify.expect(requestTo(containsString("apiKey=clave-ficticia")))
                .andRespond(withException(new SocketTimeoutException("simulado")));
        var error = assertThrows(ResponseStatusException.class, () ->
                servicio().puntosSostenibles(-12,-77,3000,AmbienteDTO.TipoPunto.RECICLAJE));
        assertEquals(503, error.getStatusCode().value());
        assertFalse(error.toString().contains("clave-ficticia"));
        servidorGeoapify.verify();
    }
}
