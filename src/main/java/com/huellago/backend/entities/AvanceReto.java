package com.huellago.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "avances_retos", uniqueConstraints = @UniqueConstraint(
        name = "uq_avances_retos_clave", columnNames = {"usuario_reto_id", "clave_solicitud"}),
        indexes = @Index(name = "ix_avances_retos_fecha", columnList = "usuario_reto_id,registrado_en"))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvanceReto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_reto_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UsuarioReto participacion;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn;

    @Column(name = "clave_solicitud", nullable = false, columnDefinition = "uuid")
    private UUID claveSolicitud;
}
