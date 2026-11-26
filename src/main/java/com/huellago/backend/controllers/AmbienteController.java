package com.huellago.backend.controllers;

import com.huellago.backend.dtos.AmbienteDTO;
import com.huellago.backend.services.AmbienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/ambiente")
@Tag(name = "Entorno ambiental", description = "Información externa orientativa; no modifica la huella de carbono del usuario.")
@SecurityRequirement(name = "bearerAuth")
public class AmbienteController {
    private final AmbienteService servicio;

    public AmbienteController(AmbienteService servicio) { this.servicio = servicio; }

    @GetMapping("/calidad-aire")
    @Operation(summary = "Consultar calidad del aire estimada", description = "Consulta las condiciones actuales del modelo CAMS mediante Open-Meteo. No son mediciones de una estación local ni emisiones de CO2.")
    @ApiResponse(responseCode = "200", description = "Datos del modelo disponibles", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Coordenadas inválidas", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AmbienteDTO.Error.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente o inválido")
    @ApiResponse(responseCode = "502", description = "Respuesta inválida del proveedor", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AmbienteDTO.Error.class)))
    @ApiResponse(responseCode = "503", description = "Integración deshabilitada o proveedor no disponible", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AmbienteDTO.Error.class)))
    public AmbienteDTO.CalidadAire calidadAire(
            @Parameter(description = "Latitud WGS84 entre -90 y 90", example = "-12.0464") @RequestParam double latitud,
            @Parameter(description = "Longitud WGS84 entre -180 y 180", example = "-77.0428") @RequestParam double longitud) {
        validarCoordenadas(latitud, longitud);
        return servicio.calidadAire(latitud, longitud);
    }

    @GetMapping("/puntos-sostenibles")
    @Operation(summary = "Buscar puntos sostenibles cercanos", description = "Consulta Geoapify Places con datos de OpenStreetMap. Devuelve hasta 20 lugares por categoría, ordenados por distancia; los metadatos opcionales pueden faltar. La capacidad no indica disponibilidad en tiempo real.")
    @ApiResponse(responseCode = "200", description = "Hasta 20 puntos; colección vacía si no hay coincidencias", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Coordenadas, radio o tipo inválidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AmbienteDTO.Error.class)))
    @ApiResponse(responseCode = "401", description = "JWT ausente o inválido")
    @ApiResponse(responseCode = "502", description = "Respuesta inválida del proveedor", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AmbienteDTO.Error.class)))
    @ApiResponse(responseCode = "503", description = "Integración deshabilitada o proveedor no disponible", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AmbienteDTO.Error.class)))
    @ApiResponse(responseCode = "429", description = "Límite local de solicitudes alcanzado", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AmbienteDTO.Error.class)))
    public AmbienteDTO.PuntosSostenibles puntos(
            @Parameter(description = "Latitud WGS84 entre -90 y 90", example = "-12.0464") @RequestParam double latitud,
            @Parameter(description = "Longitud WGS84 entre -180 y 180", example = "-77.0428") @RequestParam double longitud,
            @Parameter(description = "Radio entre 1 y 5000 metros", example = "3000") @RequestParam(defaultValue = "3000") int radioMetros,
            @Parameter(description = "RECICLAJE, ESTACIONAMIENTO_BICICLETA o ALQUILER_BICICLETA", example = "RECICLAJE")
            @RequestParam AmbienteDTO.TipoPunto tipo) {
        validarCoordenadas(latitud, longitud);
        if (radioMetros < 1 || radioMetros > 5000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Radio fuera de rango");
        return servicio.puntosSostenibles(latitud, longitud, radioMetros, tipo);
    }

    private void validarCoordenadas(double latitud, double longitud) {
        if (!Double.isFinite(latitud) || !Double.isFinite(longitud)
                || latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coordenadas fuera de rango");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("mensaje",
                error.getReason() == null ? "No se pudo consultar el proveedor" : error.getReason()));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, String>> parametrosInvalidos() {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Parámetros ambientales inválidos"));
    }
}
