package com.huellago.backend.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "resultados_retos_colectivos", uniqueConstraints =
        @UniqueConstraint(columnNames = "reto_id"))
@Data
public class ResultadoRetoColectivo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "reto_id", nullable = false, unique = true)
    private Reto reto;

    private Integer participantes;
    private BigDecimal progresoFinal;
    private String estado;
    private LocalDateTime fechaCierre;
}
