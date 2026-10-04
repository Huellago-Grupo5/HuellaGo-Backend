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

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private CategoriaHabito categoria;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "reto", fetch = FetchType.EAGER)
    private List<UsuarioReto> usuariosRetos;
}
