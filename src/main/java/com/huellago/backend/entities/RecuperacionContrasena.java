package com.huellago.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "recuperaciones_contrasena")
@Getter @Setter
public class RecuperacionContrasena {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(nullable = false, length = 255)
    private String correoDestino;
    @Column(nullable = false)
    private Instant creadoEn;
    @Column(nullable = false)
    private Instant expiraEn;
    private Instant consumidoEn;
}
