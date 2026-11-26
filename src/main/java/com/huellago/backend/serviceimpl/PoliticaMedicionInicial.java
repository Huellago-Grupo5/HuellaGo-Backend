package com.huellago.backend.serviceimpl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;

/** Umbrales orientativos internos de HuellaGo; no son límites científicos ni per cápita. */
@Component
public class PoliticaMedicionInicial {
    @Value("${huellago.medicion.electricidad.factor-kg-co2e-kwh:}")
    String factorElectricidad = "";
    @Value("${huellago.medicion.electricidad.referencia:}")
    String referenciaElectricidad = "";
    @Value("${huellago.medicion.impacto.limite-bajo:35}")
    String limiteBajo = "35";
    @Value("${huellago.medicion.impacto.limite-medio:70}")
    String limiteMedio = "70";
    @Value("${huellago.medicion.impacto.referencia:Convención académica HuellaGo: huella semanal orientativa de alcance mixto personal y del hogar; umbrales no oficiales ni per cápita}")
    String referencia = "Convención académica HuellaGo: huella semanal orientativa de alcance mixto personal y del hogar; umbrales no oficiales ni per cápita";

    BigDecimal consumoElectricoSemanal(BigDecimal consumoDiario) {
        if (referenciaElectricidad.isBlank()) throw noDisponible("Falta una referencia documentada del factor eléctrico");
        BigDecimal factor = numero(factorElectricidad);
        if (factor.signum() < 0) throw noDisponible("El factor eléctrico no puede ser negativo");
        // Consumo del hogar completo: no se multiplica nuevamente por personas.
        return consumoDiario.multiply(BigDecimal.valueOf(7)).multiply(factor);
    }

    String nivel(BigDecimal total) {
        if (total == null || total.signum() < 0) return "NO_DEFINIDO";
        if (limiteBajo.isBlank() && limiteMedio.isBlank() && referencia.isBlank()) return "NO_DEFINIDO";
        if (referencia.isBlank()) throw noDisponible("Falta la referencia de los umbrales de impacto");
        BigDecimal bajo = numero(limiteBajo), medio = numero(limiteMedio);
        if (bajo.signum() < 0 || medio.compareTo(bajo) <= 0)
            throw noDisponible("Umbrales de impacto incompatibles");
        return total.compareTo(bajo) < 0 ? "BAJO" : total.compareTo(medio) < 0 ? "MEDIO" : "ALTO";
    }

    String referenciaImpacto() {
        return referencia.isBlank() ? "Sin umbrales aprobados" : referencia;
    }

    private BigDecimal numero(String texto) {
        try { return new BigDecimal(texto); }
        catch (NumberFormatException error) { throw noDisponible("Configuración de medición ausente o inválida"); }
    }

    private ResponseStatusException noDisponible(String mensaje) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, mensaje);
    }
}
