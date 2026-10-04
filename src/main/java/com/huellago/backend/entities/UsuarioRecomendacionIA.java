package com.huellago.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios_recomendaciones_ia")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioRecomendacionIA {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String estado;
    private LocalDateTime fechaGeneracion;
    private LocalDateTime fechaCompletado;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "recomendacion_ia_id")
    private RecomendacionIA recomendacionIA;
}
