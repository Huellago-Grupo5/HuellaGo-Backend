package com.huellago.backend.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Entity
@Table(name = "insignias")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Insignia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String nombre;
    private String descripcion;
    private String imagenUrl;
    private Integer puntosRequeridos;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "insignia", fetch = FetchType.EAGER)
    private List<UsuarioInsignia> usuariosInsignias;
}
