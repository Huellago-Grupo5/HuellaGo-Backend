package com.huellago.backend.serviceimpl;

import com.huellago.backend.repositories.*;
import com.huellago.backend.services.*;
import com.huellago.backend.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** HTTP en memoria, JWT criptografico real; repositorios/selector SINTETICOS. Sin servidor ni JDBC. */
class EndpointDemoIA127Test {
    FixtureDemoIA127 f; AnnotationConfigWebApplicationContext c; MockMvc mvc;
    RecomendacionService legado; String token;
    void preparar(boolean demo) {
        f=new FixtureDemoIA127(); legado=mock(RecomendacionService.class);
        c=AplicacionDemoIA127.nuevoContexto(); c.setServletContext(new MockServletContext());
        c.addBeanFactoryPostProcessor(b->{
            b.registerSingleton("usuarios",f.usuarios); b.registerSingleton("sesiones",mock(SesionRepository.class));
            b.registerSingleton("recomendaciones",legado); b.registerSingleton("transactionManager",f.tm);
            if(demo) b.registerSingleton("entradaDemo",f.entrada);
        });
        c.register(AplicacionDemoIA127.Web.class); c.refresh();
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(c).apply(springSecurity()).build();
        token=c.getBean(JwtUtilService.class).generateToken(new UserSecurity(f.usuario));
        when(c.getBean(SesionRepository.class).existsByTokenAndActivaTrueAndFechaExpiracionAfter(eq(token),any())).thenReturn(true);
    }
    @AfterEach void cerrar() { TransactionSynchronizationManager.clear(); if(c!=null)c.close(); }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder solicitud() {
        return post("/usuarios/me/recomendaciones/generar").header("Authorization","Bearer "+token)
            .with(r->{r.setLocalAddr("127.0.0.1");r.setLocalPort(8081);r.setRemoteAddr("127.0.0.1");return r;});
    }
    @Test void legadoSinExtensionConservaDelegacionYJson() throws Exception {
        preparar(false); when(legado.generar(f.usuario)).thenReturn(new com.huellago.backend.dtos.GeneracionRecomendacionesDTO());
        mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.finalidad").doesNotExist()).andExpect(jsonPath("$.publicable").doesNotExist());
        verify(legado).generar(f.usuario); verifyNoInteractions(f.selector); assertTrue(f.guardadas.isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"SIN_ENFOQUE","AGRUPACION","NECESIDAD"})
    void tresVariantesConServiciosRealesYJwt(String enfoque) throws Exception {
        preparar(true); f.enfoque=enfoque.equals("SIN_ENFOQUE")?null:enfoque;
        mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PROPUESTA_GUARDADA_PRUEBA_INTERNA"))
            .andExpect(jsonPath("$.finalidad").value("DEMO_ACADEMICA_LOCAL")).andExpect(jsonPath("$.estadoEditorial").value("BORRADOR"))
            .andExpect(jsonPath("$.revisionHumana").value("PENDIENTE_REVISION")).andExpect(jsonPath("$.publicable").value(false));
        assertEquals(1,f.guardadas.size()); assertEquals(0,f.usuario.getEcoPuntos()); verifyNoInteractions(legado);
        assertFalse(c.containsBean("refrescoRecomendacionesListener")); assertTrue(c.getBeansOfType(javax.sql.DataSource.class).isEmpty());
    }
    @Test void reintentoConservaInstantaneaIdYFechaAunqueModeloCambieEnfoque() throws Exception {
        preparar(true); f.enfoque="AGRUPACION";
        var mapper=JsonMapper.builder().build(); var primero=mapper.readTree(mvc.perform(solicitud()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        f.enfoque="NECESIDAD";
        var segundo=mapper.readTree(mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RESULTADO_REUTILIZADO")).andReturn().getResponse().getContentAsString());
        assertEquals(primero.get("recomendaciones"),segundo.get("recomendaciones")); assertEquals(1,f.guardadas.size());
    }
    @ParameterizedTest @ValueSource(strings={"Bus","Moto"})
    void medioNoAutorizadoAntesDeInferencia(String medio) throws Exception {
        preparar(true); f.datos.get(0).setUnidad(medio); mvc.perform(solicitud()).andExpect(status().isForbidden());
        verifyNoInteractions(f.selector,legado); assertTrue(f.guardadas.isEmpty());
    }
    @Test void ceroAutorizacionesSinInferenciaNiEscritura() throws Exception {
        preparar(true); f.permisos.clear(); mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("SIN_PROPUESTA"));
        verifyNoInteractions(f.selector,legado); assertTrue(f.guardadas.isEmpty());
    }
    @Test void abstencionRealSinFallback() throws Exception {
        preparar(true); f.abstener=true; mvc.perform(solicitud()).andExpect(status().isOk()); assertTrue(f.guardadas.isEmpty()); verifyNoInteractions(legado);
    }
    @Test void accionCompatiblePeroExcluidaNoSeGuarda() throws Exception {
        preparar(true); f.accion="TR_EXPLORAR_ALTERNATIVAS_AUTO"; mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("SELECCION_RECHAZADA"));
        assertTrue(f.guardadas.isEmpty()); verifyNoInteractions(legado);
    }
    @Test void jwtObligatorioEInvalidoRechazado() throws Exception {
        preparar(true); mvc.perform(post("/usuarios/me/recomendaciones/generar")).andExpect(status().isUnauthorized());
        mvc.perform(solicitud().header("Authorization","Bearer falso").with(r->{r.removeHeader("Authorization");r.addHeader("Authorization","Bearer falso");return r;})).andExpect(status().isUnauthorized());
        verifyNoInteractions(f.selector,legado);
    }
    @Test void jwtExpiradoYOtraClaveSonRechazados() throws Exception {
        preparar(true);
        var key=io.jsonwebtoken.security.Keys.hmacShaKeyFor(Base64.getDecoder().decode(c.getEnvironment().getProperty("huellago.jwt.secret")));
        String vencido=io.jsonwebtoken.Jwts.builder().subject(EntornoHttpIA123.CORREO).expiration(new Date(0)).signWith(key).compact();
        var emisorAjeno = new JwtUtilService();
        org.springframework.test.util.ReflectionTestUtils.setField(emisorAjeno, "secretoExterno",
                Base64.getEncoder().encodeToString(new byte[32]));
        String otraClave=emisorAjeno.generateToken(new UserSecurity(f.usuario));
        for(String t:List.of(vencido,otraClave)) mvc.perform(post("/usuarios/me/recomendaciones/generar").header("Authorization","Bearer "+t)).andExpect(status().isUnauthorized());
        verifyNoInteractions(f.selector,legado); assertTrue(f.guardadas.isEmpty());
    }
    @Test void emisionJwtRequiereContrasenaSinteticaValidaYSesion() throws Exception {
        preparar(true); f.usuario.setContrasena(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("SINTETICA-solo-unitario-127"));
        mvc.perform(post(AutenticacionDemoIA127.TOKEN)).andExpect(status().isUnauthorized());
        String basic=Base64.getEncoder().encodeToString((EntornoHttpIA123.CORREO+":SINTETICA-solo-unitario-127").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var respuesta=mvc.perform(post(AutenticacionDemoIA127.TOKEN).header("Authorization","Basic "+basic)).andExpect(status().isOk()).andExpect(jsonPath("$.publicable").value(false)).andReturn();
        String emitido=JsonMapper.builder().build().readTree(respuesta.getResponse().getContentAsString()).get("access_token").asString();
        assertEquals(EntornoHttpIA123.CORREO,c.getBean(JwtUtilService.class).extractUsername(emitido));
        verify(c.getBean(SesionRepository.class)).saveAndFlush(any()); verifyNoInteractions(f.selector,legado);
    }
    @Test void usuarioNoSinteticoRechazadoPorJwt() throws Exception {
        preparar(true); f.usuario.setId(92L); mvc.perform(solicitud()).andExpect(status().isUnauthorized()); verifyNoInteractions(f.selector,legado);
    }
    @Test void bodyYParametrosNoPuedenConcederPermisos() throws Exception {
        preparar(true); mvc.perform(solicitud().content("{\"usuarioId\":91,\"publicable\":true}")).andExpect(status().isBadRequest());
        mvc.perform(solicitud().queryParam("accionId","TR_PLANIFICAR_DESPLAZAMIENTOS")).andExpect(status().isBadRequest()); verifyNoInteractions(f.selector,legado);
    }
    @Test void transaccionActivaRechazadaAntesDeInferencia() throws Exception {
        preparar(true); TransactionSynchronizationManager.setActualTransactionActive(true);
        mvc.perform(solicitud()).andExpect(status().isInternalServerError()); verifyNoInteractions(f.selector,legado);
    }
    @Test void cambioDuranteInferenciaNoPersiste() throws Exception {
        preparar(true); f.duranteInferencia=()->f.datos.get(1).setValor(new java.math.BigDecimal("101"));
        mvc.perform(solicitud()).andExpect(status().isConflict()); assertTrue(f.guardadas.isEmpty()); verifyNoInteractions(legado);
    }
    @Test void destinoHttpIncorrectoRechazado() throws Exception {
        preparar(true); mvc.perform(solicitud().with(r->{r.setLocalPort(18081);return r;})).andExpect(status().isForbidden()); verifyNoInteractions(f.selector,legado);
    }
    @Test void publicableSigueCerradoInclusoEnComposicionDePrueba() {
        preparar(true); var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new UserSecurity(f.usuario),null,List.of());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->f.generacion.generarParaPublicacionAutorizada(auth)); verifyNoInteractions(f.selector);
    }
    @Test void huellaIncorrectaYFamiliaParcialNuncaHabilitanInferencia() throws Exception {
        preparar(true); var p=f.permisos.get(0);
        f.permisos.set(0,new PoliticaAutorizacionEditorialIAService.Autorizacion(p.accionId(),p.versionAccion(),p.enfoque(),p.versionPresentacion(),"0".repeat(64),p.camposContextoPermitidos()));
        mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("SIN_PROPUESTA"));
        f.permisos.remove(0);
        mvc.perform(solicitud()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("SIN_PROPUESTA"));
        verifyNoInteractions(f.selector,legado); assertTrue(f.guardadas.isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"TIEMPO_AGOTADO","NO_DISPONIBLE"})
    void fallosOllamaControladosSinFallback(String tipo) throws Exception {
        preparar(true); var t=com.huellago.backend.exceptions.OllamaClientException.Tipo.valueOf(tipo);
        when(f.selector.generarV2ConAlcanceInterno(any(),anyList(),anySet())).thenThrow(new com.huellago.backend.exceptions.OllamaClientException(t,"SINTETICO"));
        mvc.perform(solicitud()).andExpect(status().is(t==com.huellago.backend.exceptions.OllamaClientException.Tipo.TIEMPO_AGOTADO?504:503)).andExpect(jsonPath("$.publicable").value(false));
        verifyNoInteractions(legado); assertTrue(f.guardadas.isEmpty());
    }
    @Test void falloPersistenciaControladoSinFallback() throws Exception {
        preparar(true); doThrow(new org.springframework.dao.DataIntegrityViolationException("SINTETICO")).when(f.asociaciones).saveAndFlush(any());
        mvc.perform(solicitud()).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.estado").value("PERSISTENCIA_NO_DISPONIBLE"));
        assertTrue(f.guardadas.isEmpty()); verifyNoInteractions(legado);
    }
    @Test void otrosTresEndpointsMantienenDelegacionPropietarioYCodigos() throws Exception {
        preparar(true); when(legado.historialCompletadas(f.usuario)).thenReturn(List.of());
        mvc.perform(get("/usuarios/me/recomendaciones/historial").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(put("/usuarios/me/recomendaciones/7/completar").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/usuarios/me/recomendaciones/7/convertir-en-reto").header("Authorization","Bearer "+token)).andExpect(status().isCreated());
        verify(legado).historialCompletadas(f.usuario); verify(legado).completar(7,f.usuario); verify(legado).convertirEnReto(7,f.usuario);
        verifyNoInteractions(f.selector); assertTrue(f.guardadas.isEmpty());
    }
}
