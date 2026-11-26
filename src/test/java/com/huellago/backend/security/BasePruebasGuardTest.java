package com.huellago.backend.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class BasePruebasGuardTest {
    @ParameterizedTest @ValueSource(strings={"jdbc:postgresql://localhost:5432/no_autorizada","jdbc:postgresql://externo:5432/huellagodb_hu29_test",""})
    void rechazaDestinoDistintoAntesDeCualquierConexion(String url){
        try(var contexto=new GenericApplicationContext()){
            contexto.setEnvironment(new MockEnvironment().withProperty("spring.datasource.url",url)
                    .withProperty("spring.sql.init.mode","never").withProperty("spring.jpa.hibernate.ddl-auto","validate"));
            assertThrows(IllegalStateException.class,()->new BasePruebasGuard().initialize(contexto));
        }
    }
    @ParameterizedTest @ValueSource(strings={"update","create","create-drop"})
    void rechazaDdlAutomaticoAntesDeCualquierConexion(String ddl){
        try(var contexto=new GenericApplicationContext()){
            contexto.setEnvironment(new MockEnvironment().withProperty("spring.datasource.url",BasePruebasGuard.URL)
                    .withProperty("spring.sql.init.mode","never").withProperty("spring.jpa.hibernate.ddl-auto",ddl));
            assertThrows(IllegalStateException.class,()->new BasePruebasGuard().initialize(contexto));
        }
    }
}
