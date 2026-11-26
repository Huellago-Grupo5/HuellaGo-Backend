package com.huellago.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "huellas_carbono")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HuellaCarbono {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private BigDecimal co2Total;
    private BigDecimal co2Transporte;
    private BigDecimal co2Energia;
    private BigDecimal co2Alimentacion;
    private BigDecimal co2Residuos;
    private LocalDateTime fechaCalculo;
    // Nulos en cálculos históricos: nunca se asigna una metodología retroactivamente.
    @Column(length = 40) private String metodologiaVersion;
    @Column(length = 20) private String periodoCalculo;
    @Column(length = 20) private String unidadCalculo;
    @Column(length = 40) private String perimetroCalculo;
    @Column(length = 1000) private String factoresAplicados;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    public HuellaCarbono(Long id, BigDecimal co2Total, BigDecimal co2Transporte,
            BigDecimal co2Energia, BigDecimal co2Alimentacion, BigDecimal co2Residuos,
            LocalDateTime fechaCalculo, Usuario usuario) {
        this.id = id;
        this.co2Total = co2Total;
        this.co2Transporte = co2Transporte;
        this.co2Energia = co2Energia;
        this.co2Alimentacion = co2Alimentacion;
        this.co2Residuos = co2Residuos;
        this.fechaCalculo = fechaCalculo;
        this.usuario = usuario;
    }
}
