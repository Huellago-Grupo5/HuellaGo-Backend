package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GeneracionRecomendacionesDTO {
    private Long usuarioId;
    private String metodo;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Resultado de generación, reutilización, abstención, rechazo o ausencia de propuesta")
    private String estado;
    private String mensaje;
    private String basePriorizacion;
    private Long huellaId;
    private LocalDateTime fechaCalculoHuella;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Unidad de la huella utilizada, cuando existe", example = "kg CO2e")
    private String unidad;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Periodo de la huella utilizada, cuando existe", example = "semanal")
    private String periodo;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Campos que el usuario necesita completar; puede estar vacío")
    private List<String> datosFaltantes;
    private List<String> advertencias;
    private List<RecomendacionPersonalizadaDTO> recomendaciones;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @io.swagger.v3.oas.annotations.media.Schema(description = "Finalidad editorial vigente; no equivale a publicación general")
    private String finalidad;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String estadoEditorial;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String revisionHumana;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @io.swagger.v3.oas.annotations.media.Schema(description = "Indica si existe autorización real de publicación; el uso universitario local no la concede")
    private Boolean publicable;

    /** Conserva exactamente la firma anterior; LEGADO no acredita metadatos editoriales. */
    public GeneracionRecomendacionesDTO(Long usuarioId,String metodo,String estado,String mensaje,
            String basePriorizacion,Long huellaId,LocalDateTime fechaCalculoHuella,String unidad,String periodo,
            List<String> datosFaltantes,List<String> advertencias,List<RecomendacionPersonalizadaDTO> recomendaciones) {
        this.usuarioId=usuarioId; this.metodo=metodo; this.estado=estado; this.mensaje=mensaje;
        this.basePriorizacion=basePriorizacion; this.huellaId=huellaId; this.fechaCalculoHuella=fechaCalculoHuella;
        this.unidad=unidad; this.periodo=periodo; this.datosFaltantes=datosFaltantes;
        this.advertencias=advertencias; this.recomendaciones=recomendaciones;
    }
}
