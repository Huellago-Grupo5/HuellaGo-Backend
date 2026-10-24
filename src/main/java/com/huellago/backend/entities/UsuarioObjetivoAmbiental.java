package com.huellago.backend.entities;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "usuarios_objetivos_ambientales", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "objetivo_id"})) @Data @NoArgsConstructor @AllArgsConstructor
public class UsuarioObjetivoAmbiental {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "usuario_id", nullable = false) private Usuario usuario;
    @ManyToOne(optional = false) @JoinColumn(name = "objetivo_id", nullable = false) private ObjetivoAmbiental objetivo;
}
