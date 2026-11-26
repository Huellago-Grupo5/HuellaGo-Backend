package com.huellago.backend.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Entity
@Table(name = "recomendaciones_ia")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecomendacionIA {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String titulo;
    private String descripcion;
    private String prioridad;
    private Integer puntosRecompensa;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private CategoriaHabito categoria;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "recomendacionIA", fetch = FetchType.EAGER)
    private List<UsuarioRecomendacionIA> usuariosRecomendacionesIA;

    // Opcionales: las recomendaciones heredadas no reciben identidad IA automáticamente.
    @Column(name = "accion_id", length = 100, updatable = false)
    private String accionId;

    @Column(name = "version_accion", length = 30, updatable = false)
    private String versionAccion;

    /** Conserva la firma del constructor anterior a la identidad IA. */
    public RecomendacionIA(Integer id, String titulo, String descripcion, String prioridad,
                           Integer puntosRecompensa, CategoriaHabito categoria,
                           List<UsuarioRecomendacionIA> usuariosRecomendacionesIA) {
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.prioridad = prioridad;
        this.puntosRecompensa = puntosRecompensa;
        this.categoria = categoria;
        this.usuariosRecomendacionesIA = usuariosRecomendacionesIA;
    }
}
