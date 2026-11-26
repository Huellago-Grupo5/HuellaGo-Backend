package com.huellago.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones", uniqueConstraints = @UniqueConstraint(
        name = "uq_notificaciones_usuario_evento", columnNames = {"usuario_id", "clave_evento"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Notificacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String titulo;
    private String mensaje;
    private String tipo;
    private Boolean leida;
    private LocalDateTime fechaCreacion;
    @Column(name = "clave_evento", length = 100)
    private String claveEvento;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
