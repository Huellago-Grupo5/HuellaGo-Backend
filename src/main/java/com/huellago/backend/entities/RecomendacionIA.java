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
}
