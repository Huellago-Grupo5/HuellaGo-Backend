package com.huellago.backend;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import com.huellago.backend.serviceimpl.*;
import com.huellago.backend.security.BasePruebasGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfSystemProperty(named="huellago.realDb", matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:postgresql://localhost:5432/huellagodb_hu29_test",
    "spring.sql.init.mode=never","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false",
    "spring.jpa.open-in-view=false","spring.mail.test-connection=false","huellago.recovery.enabled=false",
    "server.address=127.0.0.1","logging.level.root=WARN"})
@ContextConfiguration(initializers=BasePruebasGuard.class)
class CuentasPostgresHttpTest {
    @LocalServerPort int puerto;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioService usuarios;
    @Autowired com.huellago.backend.services.RecuperacionContrasenaService recuperaciones;
    @Autowired PlatformTransactionManager transacciones;
    @MockitoBean CorreoRecuperacionService correo;
    @MockitoBean CierreRetoColectivoProgramado scheduler;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean(name="recoveryRateClock") Clock relojLimites;
    static final AtomicReference<Instant> TIEMPO_LIMITES=new AtomicReference<>(Instant.now());
    final String clave="PruebaSegura!2026";
    final String nueva="NuevaSegura!2026";
    final List<String> creados=new CopyOnWriteArrayList<>();
    final Map<String,String> secretos=new ConcurrentHashMap<>();
    static final List<String> EVIDENCIA=new CopyOnWriteArrayList<>();
    final JsonMapper json=new JsonMapper();
    final HttpClient cliente=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @BeforeEach void comprobarYSimularCorreo() {
        TIEMPO_LIMITES.updateAndGet(t->t.plusSeconds(1800));
        doAnswer(i->TIEMPO_LIMITES.get()).when(relojLimites).instant();
        assertEquals("huellagodb_hu29_test",jdbc.queryForObject("SELECT current_database()",String.class));
        doAnswer(i->{secretos.put(i.getArgument(0),i.getArgument(1));return null;}).when(correo).enviar(any(),any());
    }
    String email(){String e="autonomo20261025-"+UUID.randomUUID()+"@example.invalid";creados.add(e);return e;}
    HttpResponse<String> http(String metodo,String ruta,String body,String token)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(30));
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(body!=null)b.header("Content-Type","application/json");
        b.method(metodo,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        var r=cliente.send(b.build(),HttpResponse.BodyHandlers.ofString());EVIDENCIA.add(metodo+" "+ruta+" -> "+r.statusCode());return r;
    }
    String registro(String e)throws Exception{
        var r=http("POST","/auth/register",json.writeValueAsString(Map.of("nombres","Prueba","apellidos","Autonoma","correo",e,"contrasena",clave)),null);
        assertEquals(201,r.statusCode());return e;
    }
    HttpResponse<String> login(String e,String p)throws Exception{return http("POST","/auth/login",json.writeValueAsString(Map.of("correo",e,"contrasena",p)),null);}
    String jwt(String e)throws Exception{var r=login(e,clave);assertEquals(200,r.statusCode());return json.readTree(r.body()).get("token").asText();}
    String solicitar(String e)throws Exception{
        TIEMPO_LIMITES.updateAndGet(t->t.plusSeconds(61));
        var r=http("POST","/auth/recuperar-contrasena",json.writeValueAsString(Map.of("correo",e)),null);
        assertEquals(200,r.statusCode());assertFalse(json.readTree(r.body()).has("token"));assertFalse(r.body().contains(secretos.get(e)));return secretos.get(e);
    }
    HttpResponse<String> reset(String t,String p)throws Exception{return http("POST","/auth/restablecer-contrasena",json.writeValueAsString(Map.of("token",t,"nuevaContrasena",p)),null);}
    Long id(String e){return jdbc.queryForObject("SELECT id FROM usuarios WHERE correo=?",Long.class,e);}
    @AfterEach void limpiarSoloRegistrosPropios(){
        for(String e:creados){
            var ids=jdbc.queryForList("SELECT id FROM usuarios WHERE correo=?",Long.class,e);
            for(Long id:ids){
                jdbc.update("DELETE FROM recuperaciones_contrasena WHERE usuario_id=?",id);
                jdbc.update("DELETE FROM sesiones WHERE usuario_id=?",id);
                jdbc.update("DELETE FROM habitos WHERE usuario_id=?",id);
                jdbc.update("DELETE FROM usuarios WHERE id=? AND correo=?",id,e);
            }
        }
    }
    @AfterAll static void evidencia()throws Exception{
        java.nio.file.Files.write(java.nio.file.Path.of("target/cuentas-http-evidencia.txt"),EVIDENCIA);
    }
    @Test void flujoCompletoRevocaJwtAnteriorRechazaReusoYPermiteNuevoLogin()throws Exception{
        String e=registro(email()),token=jwt(e),t=solicitar(e);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recuperaciones_contrasena WHERE usuario_id=? AND token_hash=?",Integer.class,id(e),RecuperacionContrasenaServiceImpl.hash(t)));
        assertEquals(204,reset(t,nueva).statusCode());
        assertEquals(400,reset(t,nueva).statusCode());
        assertEquals(401,login(e,clave).statusCode());assertEquals(200,login(e,nueva).statusCode());
        assertEquals(401,http("GET","/usuarios/me/nivel",null,token).statusCode());
        String hash=jdbc.queryForObject("SELECT contrasena FROM usuarios WHERE correo=?",String.class,e);
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(nueva,hash));
    }
    @Test void tokenExpiradoManipuladoYLegadoNoCambianClave()throws Exception{
        String e=registro(email()),t=solicitar(e);
        assertEquals(400,reset(t.substring(0,42)+(t.endsWith("A")?"B":"A"),nueva).statusCode());
        assertEquals(400,reset("eyJhbGciOiJIUzI1NiJ9.legacy.signature",nueva).statusCode());
        jdbc.update("UPDATE recuperaciones_contrasena SET expira_en=creado_en+interval '1 microsecond' WHERE usuario_id=?",id(e));
        assertEquals(400,reset(t,nueva).statusCode());assertEquals(200,login(e,clave).statusCode());
    }
    @Test void dosConsumosConcurrentesSoloPermitenUno()throws Exception{
        String e=registro(email()),t=solicitar(e);CountDownLatch inicio=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Integer> accion=()->{inicio.await();return reset(t,nueva).statusCode();};
            var a=pool.submit(accion);var b=pool.submit(accion);inicio.countDown();
            var estados=new ArrayList<>(List.of(a.get(40,TimeUnit.SECONDS),b.get(40,TimeUnit.SECONDS)));Collections.sort(estados);
            assertEquals(List.of(204,400),estados);
        }
    }
    @Test void registroConcurrenteNormalizadoSoloCreaUnaCuenta()throws Exception{
        String e=email();CountDownLatch inicio=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Integer> accion=()->{inicio.await();return http("POST","/auth/register",json.writeValueAsString(Map.of("nombres","Prueba","apellidos","Autonoma","correo",e.toUpperCase(Locale.ROOT),"contrasena",clave)),null).statusCode();};
            var a=pool.submit(accion);var b=pool.submit(accion);inicio.countDown();var estados=new ArrayList<>(List.of(a.get(40,TimeUnit.SECONDS),b.get(40,TimeUnit.SECONDS)));Collections.sort(estados);
            assertEquals(List.of(201,409),estados);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE correo=?",Integer.class,e));
        }
    }
    @Test void falloDeCorreoRevierteTokenNuevoYConservaAnterior()throws Exception{
        String e=registro(email()),t=solicitar(e);
        TIEMPO_LIMITES.updateAndGet(i->i.plusSeconds(61));
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Correo no disponible"))
                .when(correo).enviar(eq(e),any());
        assertEquals(503,http("POST","/auth/recuperar-contrasena",json.writeValueAsString(Map.of("correo",e)),null).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recuperaciones_contrasena WHERE usuario_id=?",Integer.class,id(e)));
        assertEquals(204,reset(t,nueva).statusCode());
    }
    @Test void rollbackDeResetConservaClaveSesionYToken()throws Exception{
        String e=registro(email()),token=jwt(e),t=solicitar(e);
        new TransactionTemplate(transacciones).executeWithoutResult(s->{recuperaciones.restablecer(new RestablecerContrasenaDTO(t,nueva));s.setRollbackOnly();});
        assertEquals(200,login(e,clave).statusCode());assertEquals(200,http("GET","/usuarios/me/nivel",null,token).statusCode());
        assertEquals(204,reset(t,nueva).statusCode());
    }
    @Test void ambasRutasPerfilValidanActualizanYRevocanSesionAlCambiarCorreo()throws Exception{
        for(String ruta:List.of("/auth/perfil","/usuarios/perfil")){
            String e=registro(email()),token=jwt(e);
            assertEquals(400,http("PUT",ruta,"{}",token).statusCode());
            assertEquals(401,http("PUT",ruta,"{}",null).statusCode());
            String t=solicitar(e),nuevo=email();
            var r=http("PUT",ruta,json.writeValueAsString(Map.of("id",999999,"nombres","Prueba Nueva","apellidos","Autonoma","correo",nuevo)),token);
            assertEquals(200,r.statusCode());assertEquals("true",r.headers().firstValue("X-Reauthentication-Required").orElseThrow());
            assertEquals(401,http("GET","/usuarios/me/nivel",null,token).statusCode());
            assertEquals(400,reset(t,nueva).statusCode());assertEquals(200,login(nuevo,clave).statusCode());
        }
    }
    @Test void loginLogoutNormalizaCorreoYDevuelveSoloCredenciales()throws Exception{
        String e=registro(email());var r=login(" "+e.toUpperCase(Locale.ROOT)+" ",clave);
        assertEquals(200,r.statusCode());var datos=json.readTree(r.body());
        assertEquals(3,datos.size()); assertEquals(e,datos.get("correo").asText());
        assertEquals(id(e).longValue(),datos.get("id").asLong());
        assertFalse(datos.has("configuracionInicialCompleta")); assertFalse(datos.has("categoriasPendientes"));
        String token=datos.get("token").asText(); assertFalse(token.isBlank());
        assertEquals(200,http("POST","/auth/logout",null,token).statusCode());assertEquals(401,http("GET","/usuarios/me/nivel",null,token).statusCode());
    }
    @Test void entradasInvalidasYCorreoInexistenteNoCreanDatos()throws Exception{
        assertEquals(400,http("POST","/auth/register","{}",null).statusCode());
        assertEquals(400,http("POST","/auth/login","{}",null).statusCode());
        String e=email();assertEquals(404,http("POST","/auth/recuperar-contrasena",json.writeValueAsString(Map.of("correo",e)),null).statusCode());
        assertEquals(400,reset("a".repeat(43),"").statusCode());
        assertEquals(400,reset("a".repeat(43),"debil").statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE correo=?",Integer.class,e));
    }
    @Test void nuevaSolicitudInvalidaAnteriorYNoGuardaElSecreto()throws Exception{
        String e=registro(email()),primero=solicitar(e),segundo=solicitar(e);
        assertNotEquals(primero,segundo);assertEquals(400,reset(primero,nueva).statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM recuperaciones_contrasena WHERE usuario_id=? AND token_hash IN (?,?)",Integer.class,id(e),primero,segundo));
        assertEquals(204,reset(segundo,nueva).statusCode());
    }
    @Test void perfilDuplicadoNoRevocaSesionNiModificaElUsuario()throws Exception{
        String e=registro(email()),ajeno=registro(email()),token=jwt(e);
        for(String ruta:List.of("/auth/perfil","/usuarios/perfil")){
            assertEquals(409,http("PUT",ruta,json.writeValueAsString(Map.of("nombres","Prueba","apellidos","Autonoma","correo",ajeno.toUpperCase(Locale.ROOT))),token).statusCode());
            assertEquals(200,http("GET","/usuarios/me/nivel",null,token).statusCode());
        }
        assertNotNull(id(e));
    }
    @Test void perfilSinCambioDeCorreoConservaSesionYSaldo()throws Exception{
        String e=registro(email()),token=jwt(e);
        for(String ruta:List.of("/auth/perfil","/usuarios/perfil")){
            var r=http("PUT",ruta,json.writeValueAsString(Map.of("nombres","Prueba Editada","apellidos","Autonoma","correo",e.toUpperCase(Locale.ROOT),"ecoPuntos",99999,"nivel",99)),token);
            assertEquals(200,r.statusCode());assertEquals("false",r.headers().firstValue("X-Reauthentication-Required").orElseThrow());
            assertEquals(0,json.readTree(r.body()).get("ecoPuntos").asInt());
            assertEquals(200,http("GET","/usuarios/me/nivel",null,token).statusCode());
        }
    }
    @Test void loginConcurrenteConResetNoConservaCredencialesAnteriores()throws Exception{
        String e=registro(email()),t=solicitar(e);
        try(var pool=Executors.newSingleThreadExecutor()){
            @SuppressWarnings("unchecked") Future<Integer>[] pendiente=new Future[1];
            new TransactionTemplate(transacciones).executeWithoutResult(tx->{
                jdbc.queryForObject("SELECT id FROM usuarios WHERE id=? FOR UPDATE",Long.class,id(e));
                pendiente[0]=pool.submit(()->login(e,clave).statusCode());
                recuperaciones.restablecer(new RestablecerContrasenaDTO(t,nueva));
            });
            assertEquals(401,pendiente[0].get(40,TimeUnit.SECONDS));assertEquals(200,login(e,nueva).statusCode());
        }
    }
    @Test void dosLoginsCreanSesionesDistintasYLogoutSoloRevocaLaSolicitante()throws Exception{
        String e=registro(email()),uno=jwt(e),dos=jwt(e);assertNotEquals(uno,dos);
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM sesiones WHERE usuario_id=? AND activa",Integer.class,id(e)));
        assertEquals(200,http("POST","/auth/logout",null,uno).statusCode());
        assertEquals(401,http("GET","/usuarios/me/nivel",null,uno).statusCode());
        assertEquals(200,http("GET","/usuarios/me/nivel",null,dos).statusCode());
    }
    @Test void rechazo429ConservaTokenYNoVuelveAEnviarCorreo()throws Exception{
        String e=registro(email()),t=solicitar(e);
        var rechazo=http("POST","/auth/recuperar-contrasena",json.writeValueAsString(Map.of("correo",e.toUpperCase(Locale.ROOT))),null);
        assertEquals(429,rechazo.statusCode());assertEquals("60",rechazo.headers().firstValue("Retry-After").orElseThrow());
        verify(correo,times(1)).enviar(eq(e),any());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recuperaciones_contrasena WHERE usuario_id=? AND token_hash=?",Integer.class,id(e),RecuperacionContrasenaServiceImpl.hash(t)));
        assertEquals(204,reset(t,nueva).statusCode());
    }
    @Test void concurrenciaHttpDeRecuperacionRespetaLimiteConPostgres()throws Exception{
        String e=registro(email());CountDownLatch inicio=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(6)){
            var futuros=new ArrayList<Future<Integer>>();
            for(int i=0;i<12;i++)futuros.add(pool.submit(()->{inicio.await();return http("POST","/auth/recuperar-contrasena",json.writeValueAsString(Map.of("correo",e)),null).statusCode();}));
            inicio.countDown();int exitos=0,rechazos=0;
            for(var f:futuros){int estado=f.get(40,TimeUnit.SECONDS);if(estado==200)exitos++;if(estado==429)rechazos++;}
            assertEquals(1,exitos);assertEquals(11,rechazos);verify(correo,times(1)).enviar(eq(e),any());
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recuperaciones_contrasena WHERE usuario_id=?",Integer.class,id(e)));
        }
    }
    @Test void cooldownYVentanaMovilPermitenRecuperacionPosteriorConPostgres()throws Exception{
        String e=registro(email()),ultimo=null;
        for(int i=0;i<3;i++)ultimo=solicitar(e);
        TIEMPO_LIMITES.updateAndGet(t->t.plusSeconds(61));
        assertEquals(429,http("POST","/auth/recuperar-contrasena",json.writeValueAsString(Map.of("correo",e)),null).statusCode());
        verify(correo,times(3)).enviar(eq(e),any());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recuperaciones_contrasena WHERE usuario_id=? AND token_hash=?",Integer.class,id(e),RecuperacionContrasenaServiceImpl.hash(ultimo)));
        TIEMPO_LIMITES.updateAndGet(t->t.plusSeconds(900));
        String nuevo=solicitar(e);verify(correo,times(4)).enviar(eq(e),any());
        assertEquals(204,reset(nuevo,nueva).statusCode());
    }
}
