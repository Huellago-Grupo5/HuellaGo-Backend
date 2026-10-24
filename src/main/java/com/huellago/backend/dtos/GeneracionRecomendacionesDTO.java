package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GeneracionRecomendacionesDTO {
    private Long usuarioId;
    private String metodo;
    private String estado;
    private String mensaje;
    private String basePriorizacion;
    private Long huellaId;
    private LocalDateTime fechaCalculoHuella;
    private String unidad;
    private String periodo;
    private List<String> datosFaltantes;
    private List<String> advertencias;
    private List<RecomendacionPersonalizadaDTO> recomendaciones;
}
