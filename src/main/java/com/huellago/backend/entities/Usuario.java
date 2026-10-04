package com.huellago.backend.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "usuarios")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombres;
    private String apellidos;
    private String correo;
    private String contrasena;
    private Integer ecoPuntos;
    private Integer nivel;
    private Boolean notificacionesActivas;
    private Boolean visibilidadComunidad;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
    private List<Habito> habitos;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
    private List<HuellaCarbono> huellasCarbono;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
    private List<UsuarioReto> usuariosRetos;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
    private List<UsuarioInsignia> usuariosInsignias;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
    private List<UsuarioRecomendacionIA> usuariosRecomendacionesIA;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
    private List<Notificacion> notificaciones;

    @JsonIgnore
    @ToString.Exclude
    @OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
    private List<Sesion> sesiones;
}
