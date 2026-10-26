package com.huellago.backend.security;

import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;

class OrigenRecuperacionTest {
    MockEnvironment entorno=new MockEnvironment();
    OrigenRecuperacion origen=new OrigenRecuperacion(entorno);
    @AfterEach void limpiar(){RequestContextHolder.resetRequestAttributes();}
    @Test void usaConexionIgnorandoTodasLasCabecerasForwarded(){
        var request=new MockHttpServletRequest();request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For","198.51.100.1");request.addHeader("Forwarded","for=198.51.100.2");request.addHeader("X-Real-IP","198.51.100.3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals("192.0.2.10",origen.ipActual().orElseThrow());
    }
    @Test void sinContextoHttpConservaSoloProteccionPorCuenta(){assertTrue(origen.ipActual().isEmpty());}
    @Test void wrapperQueReescribeRemoteAddrNoEludeDireccionOriginal(){
        var request=new MockHttpServletRequest();request.setRemoteAddr("192.0.2.10");
        var envoltura=new jakarta.servlet.http.HttpServletRequestWrapper(request){
            @Override public String getRemoteAddr(){return "198.51.100.10";}
        };
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(envoltura));
        assertEquals("192.0.2.10",origen.ipActual().orElseThrow());
    }
    @Test void forwardingConfiguradoNoSeUsaComoIpFiable(){
        var request=new MockHttpServletRequest();request.setRemoteAddr("192.0.2.10");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        for(String estrategia:new String[]{"framework","native"}){
            entorno.withProperty("server.forward-headers-strategy",estrategia);assertTrue(origen.ipActual().isEmpty());
        }
    }
    @Test void direccionAusenteONoLiteralNoSeUtiliza(){
        var request=new MockHttpServletRequest();RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        for(String ip:new String[]{"","nombre.example.invalid"}){request.setRemoteAddr(ip);assertTrue(origen.ipActual().isEmpty());}
    }
}
