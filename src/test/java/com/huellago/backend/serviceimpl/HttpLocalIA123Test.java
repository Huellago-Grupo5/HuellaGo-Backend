package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.exceptions.OllamaClientException;
import com.huellago.backend.repositories.*;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Spring MVC/Security reales en memoria. No Tomcat, Boot, DataSource, JPA u Ollama reales. */
class HttpLocalIA123Test {
    static final String CLAVE="Clave-sintetica-unitaria-123";
    static final String HASH=new BCryptPasswordEncoder().encode(CLAVE);
    final UsuarioRepository usuarios=mock(UsuarioRepository.class);
    final SesionRepository sesiones=mock(SesionRepository.class);
    final GeneracionManualIAService generacion=mock(GeneracionManualIAService.class);
    final TransaccionesAisladasIA tx=new TransaccionesAisladasIA();
    final Map<String,Sesion> guardadas=new HashMap<>();
    final JsonMapper mapper=JsonMapper.builder().build();
    AnnotationConfigWebApplicationContext contexto;
    MockMvc mvc;
    Usuario usuario;
    @BeforeEach void preparar() {
        usuario=new Usuario(); usuario.setId(123L); usuario.setCorreo(EntornoHttpIA123.CORREO); usuario.setContrasena(HASH);
        when(usuarios.findByCorreoNormalizado(EntornoHttpIA123.CORREO)).thenReturn(usuario);
        when(sesiones.saveAndFlush(any())).thenAnswer(i->{ Sesion s=i.getArgument(0); guardadas.put(s.getToken(),s); return s; });
        when(sesiones.findByToken(anyString())).thenAnswer(i->Optional.ofNullable(guardadas.get(i.getArgument(0))));
        when(generacion.generarParaPruebaInterna(any())).thenReturn(new ResultadoGeneracionManualIADTO(
                ResultadoGeneracionManualIADTO.Estado.SIN_PROPUESTA,null,null,null));
        contexto=new AnnotationConfigWebApplicationContext(); contexto.setServletContext(new MockServletContext());
        contexto.addBeanFactoryPostProcessor(b->{
            b.registerSingleton("usuariosMock",usuarios); b.registerSingleton("sesionesMock",sesiones);
            b.registerSingleton("generacionMock",generacion); b.registerSingleton("transactionManager",tx);
        });
        contexto.register(AplicacionHttpIA123.Web.class); contexto.refresh();
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }
    @AfterEach void cerrar() { if(contexto!=null) contexto.close(); }
    String basic(String clave) {
        return "Basic "+Base64.getEncoder().encodeToString((EntornoHttpIA123.CORREO+":"+clave).getBytes(StandardCharsets.UTF_8));
    }
    String token() throws Exception {
        var r=mvc.perform(post(EntornoHttpIA123.TOKEN).header("Authorization",basic(CLAVE))).andExpect(status().isOk()).andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).get("access_token").asString();
    }
    @Test void generarSinJwtRechazado() throws Exception {
        mvc.perform(post(EntornoHttpIA123.RUTA)).andExpect(status().isUnauthorized()); verifyNoInteractions(generacion);
    }
    @Test void tokenSinCredencialesRechazado() throws Exception {
        mvc.perform(post(EntornoHttpIA123.TOKEN)).andExpect(status().isUnauthorized()); assertTrue(guardadas.isEmpty());
    }
    @Test void claveInvalidaNoEmiteToken() throws Exception {
        mvc.perform(post(EntornoHttpIA123.TOKEN).header("Authorization",basic("incorrecta"))).andExpect(status().isUnauthorized());
        assertTrue(guardadas.isEmpty()); verifyNoInteractions(generacion);
    }
    @Test void jwtValidoIdentificaUserSecurityYDelegaSinTransaccion() throws Exception {
        when(generacion.generarParaPruebaInterna(any())).thenAnswer(i->{
            org.springframework.security.core.Authentication a=i.getArgument(0);
            assertTrue(a.isAuthenticated()); assertEquals(123L,((UserSecurity)a.getPrincipal()).getUser().getId());
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return new ResultadoGeneracionManualIADTO(ResultadoGeneracionManualIADTO.Estado.SIN_PROPUESTA,null,null,null);
        });
        mvc.perform(post(EntornoHttpIA123.RUTA).header("Authorization","Bearer "+token()).header("X-Usuario-Id","999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("SIN_PROPUESTA"))
                .andExpect(jsonPath("$.publicable").value(false)).andExpect(jsonPath("$.recomendaciones").isEmpty());
        verify(generacion,times(1)).generarParaPruebaInterna(any());
    }
    @Test void jwtMalformadoRechazado() throws Exception {
        mvc.perform(post(EntornoHttpIA123.RUTA).header("Authorization","Bearer no.es.valido")).andExpect(status().isUnauthorized());
        verifyNoInteractions(generacion);
    }
    @Test void jwtDeOtroProcesoOProduccionRechazado() throws Exception {
        var otraClave=new SeguridadHttpIA123(usuarios,sesiones);
        String ajeno=otraClave.emitir(new UserSecurity(usuario),new TransactionTemplate(tx));
        mvc.perform(post(EntornoHttpIA123.RUTA).header("Authorization","Bearer "+ajeno)).andExpect(status().isUnauthorized());
        verifyNoInteractions(generacion);
    }
    @Test void jwtSinSesionActivaRechazado() throws Exception {
        String token=token(); guardadas.get(token).setActiva(false);
        mvc.perform(post(EntornoHttpIA123.RUTA).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        verifyNoInteractions(generacion);
    }
    @Test void sesionAjenaNoPermiteSuplantacion() throws Exception {
        String token=token(); var otro=new Usuario(); otro.setId(999L); guardadas.get(token).setUsuario(otro);
        mvc.perform(post(EntornoHttpIA123.RUTA).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        verifyNoInteractions(generacion);
    }
    @Test void jwtExpiradoRechazadoCriptograficamenteSinToleranciaAmplia() throws Exception {
        Clock reloj=mock(Clock.class); Instant ahora=Instant.parse("2026-11-09T00:00:00Z"); when(reloj.instant()).thenReturn(ahora);
        var filtro=new SeguridadHttpIA123(usuarios,sesiones,reloj);
        String token=filtro.emitir(new UserSecurity(usuario),new TransactionTemplate(tx));
        when(reloj.instant()).thenReturn(ahora.plusSeconds(901));
        var req=new MockHttpServletRequest("POST",EntornoHttpIA123.RUTA); req.addHeader("Authorization","Bearer "+token);
        var res=new MockHttpServletResponse(); var cadena=mock(jakarta.servlet.FilterChain.class);
        filtro.doFilter(req,res,cadena); assertEquals(401,res.getStatus()); verifyNoInteractions(cadena);
    }
    @ParameterizedTest @ValueSource(strings={"usuarioId","accionId","versionAccion","accionesPermitidas","autorizaciones","publicable","metodoGeneracion","modelo"})
    void bodyNoPuedeAlterarUsuarioAccionesOPermisos(String campo) throws Exception {
        mvc.perform(post(EntornoHttpIA123.RUTA).header("Authorization","Bearer "+token()).contentType("application/json").content("{\""+campo+"\":\"arbitrario\"}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(generacion);
    }
    @Test void parametrosQueryRechazados() throws Exception {
        mvc.perform(post(EntornoHttpIA123.RUTA+"?usuarioId=999").header("Authorization","Bearer "+token())).andExpect(status().isBadRequest());
        verifyNoInteractions(generacion);
    }
    @Test void rutaProductivaNoRegistrada() throws Exception {
        mvc.perform(post("/usuarios/me/recomendaciones/generar").header("Authorization","Bearer "+token())).andExpect(status().isUnauthorized());
        verifyNoInteractions(generacion);
    }
    @Test void noHayHu37SchedulerDataSourceNiInicializadores() {
        assertTrue(contexto.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        assertTrue(contexto.getBeansOfType(RefrescoRecomendacionesListener.class).isEmpty());
        assertTrue(contexto.getBeansOfType(org.springframework.scheduling.TaskScheduler.class).isEmpty());
        assertTrue(contexto.getBeansOfType(jakarta.persistence.EntityManagerFactory.class).isEmpty());
        assertTrue(contexto.getBeansOfType(org.springframework.jdbc.datasource.init.DataSourceInitializer.class).isEmpty());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void respuestaDistingueGuardadaYReutilizadaSinPublicacion(boolean reutilizada) throws Exception {
        var guardada=new ResultadoPersistenciaRecomendacionesIADTO.Guardada(4,5,reutilizada,"TR_PLANIFICAR_DESPLAZAMIENTOS","1",null,"1",
                "Titulo sintetico","Paso sintetico","Motivo sintetico","Pendiente",LocalDateTime.of(2026,11,9,10,0));
        when(generacion.generarParaPruebaInterna(any())).thenReturn(new ResultadoGeneracionManualIADTO(
                ResultadoGeneracionManualIADTO.Estado.PERSISTIDA_PRUEBA_INTERNA,null,null,new ResultadoPersistenciaRecomendacionesIADTO(List.of(guardada))));
        mvc.perform(post(EntornoHttpIA123.RUTA).header("Authorization","Bearer "+token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(reutilizada?"RESULTADO_REUTILIZADO":"PROPUESTA_GUARDADA_PRUEBA_INTERNA"))
                .andExpect(jsonPath("$.recomendaciones[0].titulo").value("Titulo sintetico"))
                .andExpect(jsonPath("$.estadoEditorial").value("BORRADOR")).andExpect(jsonPath("$.revisionHumana").value("PENDIENTE_REVISION"))
                .andExpect(jsonPath("$.publicable").value(false));
    }
    @ParameterizedTest @ValueSource(strings={"ABSTENCION_MODELO","SELECCION_RECHAZADA","SIN_PROPUESTA","SIN_PRESENTACION"})
    void estadosNoGuardadosConservados(String estado) throws Exception {
        when(generacion.generarParaPruebaInterna(any())).thenReturn(new ResultadoGeneracionManualIADTO(
                ResultadoGeneracionManualIADTO.Estado.valueOf(estado),null,null,null));
        mvc.perform(post(EntornoHttpIA123.RUTA).header("Authorization","Bearer "+token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(estado)).andExpect(jsonPath("$.publicable").value(false));
    }
    @Test void conflictoDeVigenciaControlado() throws Exception {
        when(generacion.generarParaPruebaInterna(any())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"CONTEXTO_MODIFICADO"));
        mvc.perform(post(EntornoHttpIA123.RUTA).header("Authorization","Bearer "+token())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value("CONTEXTO_MODIFICADO"));
    }
    @Test void timeoutControladoNoEsAbstencion() throws Exception {
        when(generacion.generarParaPruebaInterna(any())).thenThrow(new OllamaClientException(OllamaClientException.Tipo.TIEMPO_AGOTADO,"No exponer detalles"));
        mvc.perform(post(EntornoHttpIA123.RUTA).header("Authorization","Bearer "+token())).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.estado").value("OLLAMA_TIEMPO_AGOTADO"));
    }
    @Test void errorDePersistenciaSanitizado() throws Exception {
        when(generacion.generarParaPruebaInterna(any())).thenThrow(new DataAccessResourceFailureException("No exponer detalles"));
        mvc.perform(post(EntornoHttpIA123.RUTA).header("Authorization","Bearer "+token())).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.estado").value("PERSISTENCIA_NO_DISPONIBLE"));
    }
    @Test void registroProductivoVacioSinSelectorNiEscritor() {
        var capturas=mock(CapturaContextoAmbientalIAService.class); var escritor=mock(PersistenciaRecomendacionesIAService.class);
        var orquestador=mock(OrquestadorRecomendacionesIAService.class);
        var ctx=new ContextoRecomendacionesIADTO(new ContextoRecomendacionesIADTO.Transporte("Auto",new java.math.BigDecimal("100"),5),null,null,null);
        var resultado=new ContextoRecomendacionesIAService.Resultado(ctx,List.of("Energía","Alimentación","Residuos"),List.of(),ReglasInterpretacionAmbiental.REGLAS);
        var captura=new CapturaContextoAmbientalIADTO("SINTETICO",123L,resultado,Set.of(),Set.of(),Set.of(),"sintetico");
        when(capturas.capturar(any())).thenReturn(captura);
        var real=new GeneracionManualIAServiceImpl(capturas,new PoliticaAutorizacionEditorialIAServiceImpl(),orquestador,escritor);
        var r=real.generarParaPruebaInterna(new UsernamePasswordAuthenticationToken(new UserSecurity(usuario),null,List.of()));
        assertEquals(ResultadoGeneracionManualIADTO.Estado.SIN_PROPUESTA,r.estado()); verifyNoInteractions(orquestador,escritor);
        var fixture=new RegistroEditorialPruebaInternaFixture();
        assertEquals(3,fixture.consultar().size());
        assertTrue(fixture.consultar().stream().allMatch(p->p.accionId().equals("TR_PLANIFICAR_DESPLAZAMIENTOS") && p.versionAccion().equals("1")));
    }
}
