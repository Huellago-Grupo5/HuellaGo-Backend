package com.huellago.backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "invitaciones_institucionales")
public class InvitacionInstitucional {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institucion_id", nullable = false)
    private Institucion institucion;
    @Column(name = "codigo_hash", nullable = false, unique = true, length = 64)
    private String codigoHash;
    @Column(nullable = false)
    private LocalDateTime fechaExpiracion;
    private LocalDateTime fechaUso;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usada_por_id")
    private Usuario usadaPor;
    public Long getId() { return id; }
    public Institucion getInstitucion() { return institucion; }
    public void setInstitucion(Institucion institucion) { this.institucion = institucion; }
    public String getCodigoHash() { return codigoHash; }
    public void setCodigoHash(String codigoHash) { this.codigoHash = codigoHash; }
    public LocalDateTime getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(LocalDateTime fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }
    public LocalDateTime getFechaUso() { return fechaUso; }
    public void setFechaUso(LocalDateTime fechaUso) { this.fechaUso = fechaUso; }
    public Usuario getUsadaPor() { return usadaPor; }
    public void setUsadaPor(Usuario usadaPor) { this.usadaPor = usadaPor; }
}
