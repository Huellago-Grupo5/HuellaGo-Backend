package com.huellago.backend.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Entity
@Table(name = "categorias_habito")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoriaHabito {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private String descripcion;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "categoria", fetch = FetchType.EAGER)
    private List<Habito> habitos;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "categoria", fetch = FetchType.EAGER)
    private List<Reto> retos;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "categoria", fetch = FetchType.EAGER)
    private List<RecomendacionIA> recomendacionesIA;
}
