package com.huellago.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.huellago.backend.dtos.GeneracionRecomendacionesDTO;
import com.huellago.backend.dtos.ConversionRecomendacionDTO;
import com.huellago.backend.dtos.RecomendacionCompletadaDTO;
import com.huellago.backend.dtos.RecomendacionPersonalizadaDTO;
import com.huellago.backend.dtos.EscenarioImpactoIADTO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.RecomendacionService;
import com.huellago.backend.services.EntradaDemostracionRecomendacionesIA;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recomendaciones IA", description = "Utiliza hábitos y huella vigente; Ollama selecciona entre acciones autorizadas y Java valida, presenta y persiste. No representa ahorro real medido.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/usuarios/me/recomendaciones")
public class RecomendacionController {

    /** Esquema documental del cuerpo de error real: {"mensaje":"..."}. */
    public record ErrorRespuesta(String mensaje) { }

    @Autowired
    RecomendacionService recomendacionService;
    @Autowired(required = false)
    EntradaDemostracionRecomendacionesIA demostracion;

    @Operation(summary = "Generar recomendaciones manualmente", description = "Usa hábitos y huella vigente. Ollama Qwen3 selecciona acciones elegibles; Java verifica autorización, respuesta, presentación, vigencia e idempotencia antes de persistir. Puede devolver lista vacía por falta de contexto, veto editorial o abstención. No mide ahorros reales.")
    @PostMapping("/generar")
    public ResponseEntity<GeneracionRecomendacionesDTO> generar(
            @AuthenticationPrincipal UserSecurity userSecurity, Authentication autenticacion, HttpServletRequest solicitud) {
        if (demostracion != null) return demostracion.generar(autenticacion, solicitud);
        return ResponseEntity.ok(recomendacionService.generar(userSecurity.getUser()));
    }

    @Operation(summary = "Convertir recomendación propia en reto", description = "Convertir recomendación propia en reto. Utiliza hábitos y huella vigente; Ollama selecciona entre acciones autorizadas y Java valida, presenta y persiste. No representa ahorro real medido.")
    @PostMapping("/{id}/convertir-en-reto")
    public ResponseEntity<ConversionRecomendacionDTO> convertirEnReto(
            @io.swagger.v3.oas.annotations.Parameter(description = "ID de la asociación del usuario, no de la acción canónica") @PathVariable Integer id, @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recomendacionService.convertirEnReto(id, userSecurity.getUser()));
    }

    @Operation(summary = "Completar recomendación propia", description = "Completar recomendación propia. Utiliza hábitos y huella vigente; Ollama selecciona entre acciones autorizadas y Java valida, presenta y persiste. No representa ahorro real medido.")
    @PutMapping("/{id}/completar")
    public ResponseEntity<RecomendacionCompletadaDTO> completar(
            @io.swagger.v3.oas.annotations.Parameter(description = "ID de la asociación del usuario") @PathVariable Integer id, @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(recomendacionService.completar(id, userSecurity.getUser()));
    }

    @Operation(summary = "Consultar historial de recomendaciones completadas", description = "Consultar historial de recomendaciones completadas. Utiliza hábitos y huella vigente; Ollama selecciona entre acciones autorizadas y Java valida, presenta y persiste. No representa ahorro real medido.")
    @GetMapping("/historial")
    public ResponseEntity<List<RecomendacionCompletadaDTO>> historial(
            @AuthenticationPrincipal UserSecurity userSecurity) {
        return ResponseEntity.ok(recomendacionService.historialCompletadas(userSecurity.getUser()));
    }

    @Operation(summary = "Consultar recomendaciones pendientes y escenarios hipotéticos", description = "Consulta sin llamar a Ollama. Puede filtrar por categoría y evaluar escenarios explícitos de cambio de hábitos; las reducciones en kg CO2e semanales son hipotéticas. NO_ESTIMABLE indica que faltan datos suficientes. Prioridad cualitativa y ahorro estimado son conceptos distintos.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recomendaciones pendientes propias; lista vacía si no hay", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Filtro o escenario inválido",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorRespuesta.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Contenido persistido incompleto o no recuperable",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorRespuesta.class)))
    @GetMapping
    public ResponseEntity<List<RecomendacionPersonalizadaDTO>> pendientes(
            @AuthenticationPrincipal UserSecurity userSecurity,
            @io.swagger.v3.oas.annotations.Parameter(description = "Categoría ambiental opcional", example = "Transporte") @RequestParam(required = false) String categoria,
            @io.swagger.v3.oas.annotations.Parameter(description = "Kilómetros semanales de auto hipotéticamente evitados", example = "10") @RequestParam(required = false) java.math.BigDecimal kmAutoEvitadosSemana,
            @io.swagger.v3.oas.annotations.Parameter(description = "Kilómetros semanales de auto hipotéticamente sustituidos por bicicleta", example = "5") @RequestParam(required = false) java.math.BigDecimal kmAutoSustituidosBicicletaSemana,
            @io.swagger.v3.oas.annotations.Parameter(description = "kWh diarios del hogar hipotéticamente evitados", example = "2") @RequestParam(required = false) java.math.BigDecimal kwhHogarEvitadosDia) {
        var escenario = new EscenarioImpactoIADTO(kmAutoEvitadosSemana,
                kmAutoSustituidosBicicletaSemana, kwhHogarEvitadosDia);
        return ResponseEntity.ok(escenario.presente()
                ? recomendacionService.pendientes(userSecurity.getUser(), categoria, escenario)
                : recomendacionService.pendientes(userSecurity.getUser(), categoria));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> manejarIdentificadorInvalido() {
        return ResponseEntity.badRequest().body(Map.of("mensaje",
                "El identificador de la recomendación debe ser un número entero válido"));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<Map<String, String>> manejarPersistencia() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("mensaje",
                "No se pudo procesar la recomendación. Intenta nuevamente más tarde"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> manejarError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("mensaje",
                exception.getReason() == null ? "No se pudo convertir la recomendación" : exception.getReason()));
    }
}
