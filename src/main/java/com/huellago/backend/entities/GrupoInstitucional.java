package com.huellago.backend.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "grupos_institucionales", uniqueConstraints = @UniqueConstraint(
        name = "uk_grupo_institucion_nombre", columnNames = {"institucion_id", "nombre"}))
public class GrupoInstitucional {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institucion_id", nullable = false)
    private Institucion institucion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TipoGrupoInstitucional tipo;
    @Column(nullable = false, length = 100)
    private String nombre;
    public Long getId() { return id; }
    public Institucion getInstitucion() { return institucion; }
    public void setInstitucion(Institucion institucion) { this.institucion = institucion; }
    public TipoGrupoInstitucional getTipo() { return tipo; }
    public void setTipo(TipoGrupoInstitucional tipo) { this.tipo = tipo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
