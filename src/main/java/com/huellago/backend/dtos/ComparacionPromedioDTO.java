package com.huellago.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComparacionPromedioDTO {
    private boolean comparacionDisponible;
    private BigDecimal huellaUsuario;
    private BigDecimal promedioGeneral;
    private String posicion;
    private BigDecimal diferenciaAbsoluta;
    private BigDecimal diferenciaPorcentual;
    private long usuariosConDatos;
    private String unidad;
    private String periodo;
    private String mensaje;
}
