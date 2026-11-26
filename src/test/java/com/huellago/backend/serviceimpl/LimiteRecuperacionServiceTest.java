package com.huellago.backend.serviceimpl;

import com.huellago.backend.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LimiteRecuperacionServiceTest {
    final Clock reloj=mock(Clock.class);
    final OrigenRecuperacion origen=mock(OrigenRecuperacion.class);
    Instant ahora=Instant.parse("2026-10-25T12:00:00Z");
    final MockEnvironment entorno=new MockEnvironment();
    LimiteRecuperacionServiceImpl crear(){
        when(reloj.instant()).thenAnswer(i->ahora);when(origen.ipActual()).thenReturn(Optional.empty());
        return new LimiteRecuperacionServiceImpl(entorno,reloj,origen);
    }
    @Test void correoNormalizadoTieneMismoLimiteYRechazosNoLoProlongan(){
        var limite=crear();limite.reservar(" CUENTA@EXAMPLE.INVALID ");
        assertEquals(60,assertThrows(LimiteRecuperacionException.class,()->limite.reservar("cuenta@example.invalid")).getSegundosEspera());
        ahora=ahora.plusSeconds(59);assertEquals(1,assertThrows(LimiteRecuperacionException.class,()->limite.reservar("cuenta@example.invalid")).getSegundosEspera());
        ahora=ahora.plusSeconds(1);assertDoesNotThrow(()->limite.reservar("cuenta@example.invalid"));
    }
    @Test void tresIntentosEnVentanaMovilYRecuperacionAlExpirar(){
        var limite=crear();limite.reservar("cuenta@example.invalid");ahora=ahora.plusSeconds(60);
        limite.reservar("cuenta@example.invalid");ahora=ahora.plusSeconds(60);limite.reservar("cuenta@example.invalid");
        ahora=ahora.plusSeconds(60);assertEquals(720,assertThrows(LimiteRecuperacionException.class,()->limite.reservar("cuenta@example.invalid")).getSegundosEspera());
        ahora=ahora.plusSeconds(720);assertDoesNotThrow(()->limite.reservar("cuenta@example.invalid"));
    }
    @Test void cuotaIpSeComparteEntreCuentasSinConsumirCuentaRechazada(){
        entorno.withProperty("huellago.recovery.rate.ip-limit","2");var limite=crear();
        when(origen.ipActual()).thenReturn(Optional.of("192.0.2.1"));
        limite.reservar("uno@example.invalid");limite.reservar("dos@example.invalid");
        assertThrows(LimiteRecuperacionException.class,()->limite.reservar("tres@example.invalid"));
        when(origen.ipActual()).thenReturn(Optional.of("192.0.2.2"));
        assertDoesNotThrow(()->limite.reservar("tres@example.invalid"));
    }
    @Test void ipVuelveAAdmitirSolicitudesAlExpirarVentana(){
        entorno.withProperty("huellago.recovery.rate.ip-limit","1");var limite=crear();when(origen.ipActual()).thenReturn(Optional.of("192.0.2.1"));
        limite.reservar("uno@example.invalid");assertThrows(LimiteRecuperacionException.class,()->limite.reservar("dos@example.invalid"));
        ahora=ahora.plusSeconds(900);assertDoesNotThrow(()->limite.reservar("dos@example.invalid"));
    }
    @Test void intentosSimultaneosNoEludenCooldown()throws Exception{
        var limite=crear();CountDownLatch inicio=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(8)){
            var resultados=new ArrayList<Future<Boolean>>();
            for(int i=0;i<32;i++)resultados.add(pool.submit(()->{inicio.await();try{limite.reservar("cuenta@example.invalid");return true;}catch(LimiteRecuperacionException error){return false;}}));
            inicio.countDown();int admitidos=0;for(var r:resultados)if(r.get(5,TimeUnit.SECONDS))admitidos++;
            assertEquals(1,admitidos);
        }
    }
    @Test void intentosSimultaneosDeDistintasCuentasRespetanCuotaIp()throws Exception{
        entorno.withProperty("huellago.recovery.rate.ip-limit","5");var limite=crear();when(origen.ipActual()).thenReturn(Optional.of("192.0.2.1"));
        CountDownLatch inicio=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(8)){
            var resultados=new ArrayList<Future<Boolean>>();
            for(int i=0;i<32;i++){String e="cuenta"+i+"@example.invalid";resultados.add(pool.submit(()->{inicio.await();try{limite.reservar(e);return true;}catch(LimiteRecuperacionException error){return false;}}));}
            inicio.countDown();int admitidos=0;for(var r:resultados)if(r.get(5,TimeUnit.SECONDS))admitidos++;
            assertEquals(5,admitidos);
        }
    }
    @Test void capacidadEsAcotadaNoExpulsaCuotasActivasYSeLimpia(){
        entorno.withProperty("huellago.recovery.rate.max-entries","2");var limite=crear();
        limite.reservar("uno@example.invalid");limite.reservar("dos@example.invalid");
        assertThrows(LimiteRecuperacionException.class,()->limite.reservar("tres@example.invalid"));
        assertThrows(LimiteRecuperacionException.class,()->limite.reservar("uno@example.invalid"));
        ahora=ahora.plusSeconds(900);limite.limpiarExpirados();
        assertTrue(((Map<?,?>)org.springframework.test.util.ReflectionTestUtils.getField(limite,"intentos")).isEmpty());
        assertDoesNotThrow(()->limite.reservar("tres@example.invalid"));
    }
    @Test void memoriaNoConservaCorreoNiIpEnTextoClaro(){
        var limite=crear();when(origen.ipActual()).thenReturn(Optional.of("192.0.2.1"));limite.reservar("cuenta@example.invalid");
        String estado=org.springframework.test.util.ReflectionTestUtils.getField(limite,"intentos").toString();
        assertFalse(estado.contains("cuenta@example.invalid"));assertFalse(estado.contains("192.0.2.1"));
    }
    @Test void configuracionInvalidaNoDeshabilitaLaProteccion(){
        entorno.withProperty("huellago.recovery.rate.account-limit","0");
        assertThrows(IllegalArgumentException.class,this::crear);
    }
    @Test void retryAfterRedondeaHaciaArriba(){
        var limite=crear();limite.reservar("cuenta@example.invalid");ahora=ahora.plusMillis(1);
        assertEquals(60,assertThrows(LimiteRecuperacionException.class,()->limite.reservar("cuenta@example.invalid")).getSegundosEspera());
    }
}
