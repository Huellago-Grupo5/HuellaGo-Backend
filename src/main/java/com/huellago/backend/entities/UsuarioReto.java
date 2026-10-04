package com.huellago.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private LocalDateTime fechaAceptacion;
    private LocalDateTime fechaCompletado;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "reto_id")
    private Reto reto;
}
