package com.huellago.backend.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "retos")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Reto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String titulo;
    private String descripcion;
    private Integer puntosRecompensa;
    private String dificultad;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Boolean activo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoReto tipo = TipoReto.INDIVIDUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institucion_id")
    @ToString.Exclude
    private Institucion institucion;

    @ManyToOne
    @JoinColumn(name = "usuario_recomendacion_ia_id", unique = true)
    private UsuarioRecomendacionIA recomendacionOrigen;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private CategoriaHabito categoria;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "reto", fetch = FetchType.EAGER)
    private List<UsuarioReto> usuariosRetos;
}
