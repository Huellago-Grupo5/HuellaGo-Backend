package com.huellago.backend.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public final class AmbienteDTO {
    private AmbienteDTO() { }

    public record Error(@Schema(description = "Descripción breve del error", example = "Proveedor ambiental no disponible")
                        String mensaje) { }

    public record CalidadAire(
            @Schema(description = "Latitud de la celda de referencia del modelo") double latitud,
            @Schema(description = "Longitud de la celda de referencia del modelo") double longitud,
            @Schema(description = "Fecha y hora UTC de los datos del modelo") String fechaHora,
            @Schema(description = "Índice estadounidense de calidad del aire, sin unidad") int indiceCalidadAireUs,
            @Schema(description = "PM2.5 en microgramos por metro cúbico") double pm25,
            @Schema(description = "PM10 en microgramos por metro cúbico") double pm10,
            @Schema(description = "Dióxido de nitrógeno en microgramos por metro cúbico") double dioxidoNitrogeno,
            String unidadConcentraciones, String fuente, String atribucion, String advertencia) { }

    public enum TipoPunto { RECICLAJE, ESTACIONAMIENTO_BICICLETA, ALQUILER_BICICLETA }

    public record Punto(String osmId, String nombre, TipoPunto tipo, double latitud,
                        double longitud, @Schema(description = "Distancia en línea recta, no recorrido real")
                        double distanciaMetros, String placeId, String direccion, String operador,
                        String horario, @Schema(description = "Claves de materiales marcados true por el proveedor; null si no hay información")
                        List<String> materialesAceptados,
                        @Schema(description = "Capacidad instalada, no disponibilidad en tiempo real") Integer capacidad,
                        Boolean techado, Boolean gratuito, String acceso) { }

    public record PuntosSostenibles(double latitud, double longitud, int radioMetros,
                                    TipoPunto tipo, List<Punto> puntos, String fuente,
                                    String atribucion, String advertencia) { }
}
