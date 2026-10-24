package com.huellago.backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios_instituciones", uniqueConstraints = @UniqueConstraint(
        name = "uk_usuarios_instituciones", columnNames = {"usuario_id", "institucion_id"}))
public class UsuarioInstitucion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institucion_id", nullable = false)
    private Institucion institucion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private RolInstitucional rol;
    @Column(name = "fecha_afiliacion", nullable = false)
    private LocalDateTime fechaAfiliacion;

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Institucion getInstitucion() { return institucion; }
    public void setInstitucion(Institucion institucion) { this.institucion = institucion; }
    public RolInstitucional getRol() { return rol; }
    public void setRol(RolInstitucional rol) { this.rol = rol; }
    public LocalDateTime getFechaAfiliacion() { return fechaAfiliacion; }
    public void setFechaAfiliacion(LocalDateTime fechaAfiliacion) { this.fechaAfiliacion = fechaAfiliacion; }
}
