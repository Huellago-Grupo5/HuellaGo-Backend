package com.huellago.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Sesion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String token;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaExpiracion;
    private Boolean activa;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
