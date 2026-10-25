package com.huellago.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "publicaciones_comunitarias", uniqueConstraints = {
        @UniqueConstraint(name = "uq_publicacion_autor_insignia", columnNames = {"autor_id", "insignia_id"}),
        @UniqueConstraint(name = "uq_publicacion_participacion", columnNames = "usuario_reto_id")
})
@Getter
@Setter
public class PublicacionComunitaria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;
    @ManyToOne
    @JoinColumn(name = "insignia_id")
    private Insignia insignia;
    @ManyToOne
    @JoinColumn(name = "usuario_reto_id")
    private UsuarioReto participacion;
    @Column(nullable = false, length = 255)
    private String titulo;
    @Column(nullable = false)
    private LocalDateTime fechaPublicacion;
}
