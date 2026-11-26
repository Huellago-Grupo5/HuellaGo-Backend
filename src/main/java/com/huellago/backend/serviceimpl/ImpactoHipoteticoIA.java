package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.dtos.EscenarioImpactoIADTO;
import com.huellago.backend.dtos.RecomendacionPersonalizadaDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Escenarios declarados por el usuario sobre hábitos actuales; no modifica recetas ni instantáneas. */
final class ImpactoHipoteticoIA {
    private ImpactoHipoteticoIA() { }

    static void validar(EscenarioImpactoIADTO escenario, ContextoRecomendacionesIADTO contexto) {
        validarCantidad(escenario.kmAutoEvitadosSemana());
        validarCantidad(escenario.kmAutoSustituidosBicicletaSemana());
        validarCantidad(escenario.kwhHogarEvitadosDia());
        if (escenario.kmAutoEvitadosSemana() != null || escenario.kmAutoSustituidosBicicletaSemana() != null) {
            var transporte = contexto.transporte();
            if (transporte == null || !"Auto".equalsIgnoreCase(transporte.medio()))
                throw error("El escenario de kilómetros requiere un hábito vigente de Auto");
            BigDecimal total = cero(escenario.kmAutoEvitadosSemana())
                    .add(cero(escenario.kmAutoSustituidosBicicletaSemana()));
            if (transporte.kmSemana() == null || total.compareTo(transporte.kmSemana()) > 0)
                throw error("Los kilómetros propuestos superan el recorrido semanal registrado");
        }
        if (escenario.kwhHogarEvitadosDia() != null) {
            var energia = contexto.energia();
            if (energia == null || !"Electricidad".equalsIgnoreCase(energia.fuente())
                    || energia.consumoKwhDiario() == null)
                throw error("El escenario eléctrico requiere consumo diario registrado del hogar");
            if (escenario.kwhHogarEvitadosDia().compareTo(energia.consumoKwhDiario()) > 0)
                throw error("Los kWh propuestos superan el consumo diario registrado del hogar");
        }
    }

    static void aplicar(RecomendacionPersonalizadaDTO dto, String accionId, EscenarioImpactoIADTO escenario,
            PoliticaMedicionInicial politica) {
        BigDecimal cantidad;
        BigDecimal beneficio;
        String supuestos;
        String metodo;
        String limites;
        switch (accionId) {
            case "TR_PLANIFICAR_DESPLAZAMIENTOS" -> {
                cantidad = escenario.kmAutoEvitadosSemana();
                if (cantidad == null) return;
                beneficio = HuellaCarbonoServiceImpl.emisionesTransporteSemanal("Auto", cantidad);
                supuestos = cantidad.toPlainString() + " km semanales de Auto evitados hipotéticamente, declarados por el usuario.";
                metodo = "Escenario semanal: km de Auto evitados × factor de Auto del cálculo HuellaGo ("
                        + HuellaCarbonoServiceImpl.emisionesTransporteSemanal("Auto", BigDecimal.ONE).toPlainString()
                        + " kg CO2e/km).";
                limites = "No se conoce si las gestiones pueden evitarse ni los desplazamientos alternativos. No acredita ahorro realizado.";
            }
            case "TR_EXPLORAR_ALTERNATIVAS_AUTO" -> {
                cantidad = escenario.kmAutoSustituidosBicicletaSemana();
                if (cantidad == null) return;
                beneficio = HuellaCarbonoServiceImpl.emisionesTransporteSemanal("Auto", cantidad)
                        .subtract(HuellaCarbonoServiceImpl.emisionesTransporteSemanal("Bicicleta", cantidad));
                supuestos = cantidad.toPlainString() + " km semanales de Auto sustituidos hipotéticamente por bicicleta, declarados por el usuario.";
                metodo = "Escenario semanal: emisiones de Auto menos emisiones directas de Bicicleta, con factores actuales de HuellaGo ("
                        + HuellaCarbonoServiceImpl.emisionesTransporteSemanal("Auto", BigDecimal.ONE).toPlainString()
                        + " y " + HuellaCarbonoServiceImpl.emisionesTransporteSemanal("Bicicleta", BigDecimal.ONE).toPlainString()
                        + " kg CO2e/km).";
                limites = "La disponibilidad, seguridad y viabilidad de la bicicleta no están registradas; el modelo no incluye ciclo de vida. No acredita sustitución realizada.";
            }
            case "EN_REVISAR_USOS" -> {
                cantidad = escenario.kwhHogarEvitadosDia();
                if (cantidad == null) return;
                beneficio = politica.consumoElectricoSemanal(cantidad);
                supuestos = cantidad.toPlainString() + " kWh diarios del hogar evitados hipotéticamente, declarados por el usuario.";
                metodo = "Escenario semanal: kWh diarios del hogar × 7 × factor eléctrico configurado en el cálculo HuellaGo ("
                        + politica.consumoElectricoSemanal(BigDecimal.ONE).divide(BigDecimal.valueOf(7)).toPlainString()
                        + " kg CO2e/kWh).";
                limites = "Corresponde al hogar completo, no a emisiones individuales. No se conoce si el consumo es evitable ni se acredita ahorro realizado.";
            }
            default -> { return; }
        }
        dto.setImpactoEsperado("ESTIMACION_HIPOTETICA");
        dto.setImpactoPotencialKgCo2eSemanal(beneficio.setScale(2, RoundingMode.HALF_UP));
        dto.setSupuestosImpacto(supuestos);
        dto.setMetodologiaImpacto(metodo);
        dto.setLimitacionesImpacto(limites);
        dto.setExplicacionImpacto("Beneficio potencial del escenario declarado; no es una reducción medida ni conseguida. La prioridad cualitativa expresa actuación, no magnitud de ahorro.");
    }

    private static BigDecimal cero(BigDecimal valor) { return valor == null ? BigDecimal.ZERO : valor; }
    private static void validarCantidad(BigDecimal valor) {
        if (valor != null && (valor.signum() < 0 || valor.scale() > 3 || valor.precision() > 12))
            throw error("La cantidad hipotética debe ser no negativa y tener hasta tres decimales");
    }
    private static ResponseStatusException error(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
