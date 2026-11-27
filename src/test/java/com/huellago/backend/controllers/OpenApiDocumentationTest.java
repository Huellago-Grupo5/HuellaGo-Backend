package com.huellago.backend.controllers;

import com.huellago.backend.repositories.SesionRepository;
import com.huellago.backend.security.*;
import com.huellago.backend.serviceimpl.ReporteInstitucionalCsv;
import com.huellago.backend.services.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;

import javax.sql.DataSource;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AmbienteController.class, CampanaInstitucionalController.class, DashboardController.class,
        DashboardInstitucionalController.class, EcoPuntosController.class, GrupoInstitucionalController.class,
        HabitoController.class, HuellaCarbonoController.class, InsigniaController.class,
        InstitucionController.class, LogrosController.class, NotificacionController.class,
        ObjetivoAmbientalController.class, PreferenciaNotificacionController.class, PreferenciasVisualesController.class, PrivacidadController.class,
        PublicacionComunitariaController.class, RankingComunitarioController.class,
        RecomendacionController.class, ReporteInstitucionalController.class,
        RetoColectivoController.class, RetoController.class, RetosActivosController.class,
        SesionController.class, UsuarioController.class, UsuarioPerfilController.class})
@Import({SecurityConfiguration.class, JwtRequestFilter.class, JwtUtilService.class, OpenApiConfiguration.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class, SpringDocConfiguration.class,
        SpringDocWebMvcConfiguration.class, SwaggerConfig.class,
        org.springdoc.core.properties.SwaggerUiConfigProperties.class,
        org.springdoc.core.properties.SwaggerUiOAuthProperties.class,
        org.springdoc.core.properties.SpringDocConfigProperties.class})
@TestPropertySource(properties = {"huellago.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.sql.init.mode=never", "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
class OpenApiDocumentationTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext contexto;
    @Autowired RequestMappingHandlerMapping mappings;
    @MockitoBean CampanaInstitucionalService campanas;
    @MockitoBean AmbienteService ambiente;
    @MockitoBean DashboardService dashboard;
    @MockitoBean ReporteInstitucionalService reportes;
    @MockitoBean UsuarioService usuarios;
    @MockitoBean GrupoInstitucionalService grupos;
    @MockitoBean HabitoService habitos;
    @MockitoBean UsuarioAuthorizationService propietario;
    @MockitoBean HuellaCarbonoService huellas;
    @MockitoBean ReporteAmbientalService pdf;
    @MockitoBean InsigniaService insignias;
    @MockitoBean InstitucionService instituciones;
    @MockitoBean LogrosService logros;
    @MockitoBean NotificacionService notificaciones;
    @MockitoBean ObjetivoAmbientalService objetivos;
    @MockitoBean PublicacionComunitariaService publicaciones;
    @MockitoBean RecomendacionService recomendaciones;
    @MockitoBean EntradaDemostracionRecomendacionesIA demostracion;
    @MockitoBean RetoColectivoService colectivos;
    @MockitoBean RetoService retos;
    @MockitoBean SesionService sesiones;
    @MockitoBean AccesoCuentaService acceso;
    @MockitoBean ReporteInstitucionalCsv csv;
    @MockitoBean UserDetailsService detalles;
    @MockitoBean SesionRepository sesionesRepo;

    @Test void documentoSeGeneraSinBaseDeDatos() throws Exception {
        assertTrue(contexto.getBeansOfType(DataSource.class).isEmpty());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("HuellaGo API"));
    }

    private JsonNode documento() throws Exception {
        byte[] json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return new ObjectMapper().readTree(json);
    }

    @Test void losMappingsFuncionalesTienenOperacionDescripcionYEtiqueta() throws Exception {
        JsonNode rutas = documento().path("paths");
        int encontradas = 0;
        Set<String> publicas = Set.of("POST /auth/register", "POST /auth/login",
                "POST /auth/recuperar-contrasena", "POST /auth/restablecer-contrasena");
        for (var entrada : mappings.getHandlerMethods().entrySet()) {
            if (!entrada.getValue().getBeanType().isAnnotationPresent(RestController.class)
                    || !entrada.getValue().getBeanType().getPackageName()
                    .equals("com.huellago.backend.controllers")) continue;
            for (String ruta : entrada.getKey().getPatternValues()) {
                for (var metodo : entrada.getKey().getMethodsCondition().getMethods()) {
                    String verbo = metodo.name();
                    JsonNode operacion = rutas.path(ruta).path(verbo.toLowerCase(Locale.ROOT));
                    assertFalse(operacion.isMissingNode(), verbo + " " + ruta);
                    assertFalse(operacion.path("summary").asText().isBlank(), verbo + " " + ruta);
                    assertFalse(operacion.path("description").asText().isBlank(), verbo + " " + ruta);
                    assertTrue(operacion.path("tags").size() > 0, verbo + " " + ruta);
                    assertTrue(operacion.path("responses").size() > 0, verbo + " " + ruta);
                    assertTrue(operacion.path("responses").has("200")
                            || operacion.path("responses").has("201")
                            || operacion.path("responses").has("204"), verbo + " " + ruta + " sin respuesta exitosa");
                    if (publicas.contains(verbo + " " + ruta)) {
                        assertEquals(0, operacion.path("security").size(), verbo + " " + ruta);
                    } else {
                        assertEquals("bearerAuth", operacion.path("security").get(0).fieldNames().next(),
                                verbo + " " + ruta);
                    }
                    encontradas++;
                }
            }
        }
        assertEquals(87, encontradas);
    }

    @Test void jwtParametrosYArchivosAparecenEnElEsquemaReal() throws Exception {
        JsonNode api = documento();
        assertEquals("http", api.path("components").path("securitySchemes").path("bearerAuth").path("type").asText());
        assertEquals("bearer", api.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText());
        assertTrue(api.path("paths").path("/ambiente/calidad-aire").path("get")
                .path("parameters").toString().contains("Accept-Language"));
        for (String ruta : java.util.List.of("/ambiente/calidad-aire", "/ambiente/puntos-sostenibles")) {
            JsonNode operacion = api.path("paths").path(ruta).path("get");
            assertEquals("bearerAuth", operacion.path("security").get(0).fieldNames().next());
            assertTrue(operacion.path("responses").has("200"));
            JsonNode contenido = operacion.path("responses").path("200").path("content");
            assertFalse(contenido.path("application/json").path("schema").isMissingNode()
                    && contenido.path("*/*").path("schema").isMissingNode());
            assertTrue(operacion.path("responses").has("401"));
            assertTrue(operacion.path("responses").has("503"));
            for (String estado : java.util.List.of("400", "502", "503")) {
                JsonNode error = operacion.path("responses").path(estado)
                        .path("content").path("application/json").path("schema");
                assertFalse(error.isMissingNode(), ruta + " " + estado);
                assertTrue(error.toString().contains("Error"), ruta + " " + estado);
                assertNotEquals(contenido.path("application/json").path("schema"), error);
            }
            assertTrue(operacion.path("parameters").toString().contains("latitud"));
        }
        JsonNode puntos = api.path("paths").path("/ambiente/puntos-sostenibles").path("get");
        assertTrue(puntos.path("description").asText().contains("Geoapify"));
        JsonNode limite = puntos.path("responses").path("429")
                .path("content").path("application/json").path("schema");
        assertTrue(limite.toString().contains("Error"));
        assertNotEquals(puntos.path("responses").path("200").path("content")
                .path("application/json").path("schema"), limite);
        assertTrue(api.path("paths").path("/auth/register").path("post").path("responses").has("201"));
        assertTrue(api.path("paths").path("/usuarios/me/recomendaciones/{id}/convertir-en-reto")
                .path("post").path("responses").has("201"));
        assertTrue(api.path("paths").path("/sesiones/actual").path("delete").path("responses").has("204"));
        assertTrue(api.path("paths").path("/usuarios/me/recomendaciones/generar")
                .path("post").path("responses").has("503"));
        assertTrue(api.path("paths").path("/usuarios/me/recomendaciones/{id}/completar")
                .path("put").path("responses").has("409"));
        JsonNode recomendaciones = api.path("paths").path("/usuarios/me/recomendaciones").path("get");
        for (String estado : java.util.List.of("400", "500")) {
            JsonNode error = recomendaciones.path("responses").path(estado)
                    .path("content").path("application/json").path("schema");
            assertTrue(error.toString().contains("ErrorRespuesta"), "GET recomendaciones " + estado);
        }
        assertTrue(recomendaciones.path("parameters").toString().contains("kmAutoEvitadosSemana"));
        assertTrue(recomendaciones.path("parameters").toString().contains("categoria"));
        JsonNode contenidoRecomendaciones = recomendaciones.path("responses").path("200").path("content");
        assertFalse(contenidoRecomendaciones.path("application/json").path("schema").isMissingNode()
                && contenidoRecomendaciones.path("*/*").path("schema").isMissingNode());
        assertFalse(api.path("paths").path("/auth/login").path("post")
                .path("requestBody").path("content").path("application/json").path("schema").isMissingNode());
        JsonNode pdf = api.path("paths").path("/huella-carbono/reporte").path("get")
                .path("responses").path("200").path("content").path("application/pdf");
        assertEquals("binary", pdf.path("schema").path("format").asText());
        JsonNode csv = api.path("paths").path("/instituciones/{institucionId}/reportes/desempeno-ambiental/descargar")
                .path("get").path("responses").path("200").path("content").path("text/csv");
        assertEquals("binary", csv.path("schema").path("format").asText());
    }

    @Test void documentacionPublicaYRecursosPrivadosProtegidos() throws Exception {
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/usuarios/me/recomendaciones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ambiente/calidad-aire").param("latitud", "-12").param("longitud", "-77"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/ambiente/puntos-sostenibles").param("latitud", "-12")
                .param("longitud", "-77").param("tipo", "RECICLAJE"))
                .andExpect(status().isUnauthorized());
    }
}
