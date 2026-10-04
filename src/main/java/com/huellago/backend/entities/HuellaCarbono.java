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

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
