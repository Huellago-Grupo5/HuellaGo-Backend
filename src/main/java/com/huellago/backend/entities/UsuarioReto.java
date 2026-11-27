package com.huellago.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "usuarios_retos")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioReto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String estado;
    private BigDecimal progreso;
    // Se mantiene nullable durante el ddl-auto=update; V6 backfill y endurece la columna.
    @Column(name = "cantidad_acumulada", precision = 12, scale = 3)
    private BigDecimal cantidadAcumulada = BigDecimal.ZERO;
    private LocalDateTime fechaAceptacion;
    private LocalDateTime fechaCompletado;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "reto_id")
    private Reto reto;

    @OneToMany(mappedBy = "participacion", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<AvanceReto> avances = new ArrayList<>();
}
