package com.huellago.backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "instituciones", uniqueConstraints = @UniqueConstraint(
        name = "uk_instituciones_pais_identificador", columnNames = {"pais", "identificador_fiscal"}))
public class Institucion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TipoInstitucion tipo;
    @Column(nullable = false, length = 150)
    private String nombre;
    @Column(nullable = false, length = 2)
    private String pais;
    @Column(name = "identificador_fiscal", nullable = false, length = 30)
    private String identificadorFiscal;
    @Column(name = "correo_contacto", nullable = false, length = 254)
    private String correoContacto;
    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    public Long getId() { return id; }
    public TipoInstitucion getTipo() { return tipo; }
    public void setTipo(TipoInstitucion tipo) { this.tipo = tipo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }
    public String getIdentificadorFiscal() { return identificadorFiscal; }
    public void setIdentificadorFiscal(String identificadorFiscal) { this.identificadorFiscal = identificadorFiscal; }
    public String getCorreoContacto() { return correoContacto; }
    public void setCorreoContacto(String correoContacto) { this.correoContacto = correoContacto; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
}
