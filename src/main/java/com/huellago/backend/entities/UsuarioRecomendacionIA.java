package com.huellago.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios_recomendaciones_ia")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioRecomendacionIA {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String estado;
    private LocalDateTime fechaGeneracion;
    private LocalDateTime fechaCompletado;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "recomendacion_ia_id")
    private RecomendacionIA recomendacionIA;

    // Instantánea opcional, escrita al insertar. No cambia el estado de realización.
    // updatable=false excluye estos campos de UPDATE de JPA; no vuelve inmutable el objeto
    // ni protege frente a SQL externo. El servicio futuro debe impedir su sustitución.
    @Column(name = "titulo_instantanea", length = 160, updatable = false)
    private String tituloInstantanea;

    @Column(name = "descripcion_instantanea", length = 1000, updatable = false)
    private String descripcionInstantanea;

    @Column(name = "motivo_instantanea", length = 1000, updatable = false)
    private String motivoInstantanea;

    // null significa que el modelo no seleccionó un enfoque, no un enfoque elegido por Java.
    @Column(name = "enfoque", length = 100, updatable = false)
    private String enfoque;

    @Column(name = "version_presentacion", length = 30, updatable = false)
    private String versionPresentacion;

    @Column(name = "metodo_generacion", length = 100, updatable = false)
    private String metodoGeneracion;

    // Referencia opaca o digest de vigencia; nunca el contexto personal completo.
    @Column(name = "referencia_contexto", length = 128, updatable = false)
    private String referenciaContexto;

    @Column(name = "prioridad_instantanea", length = 20, updatable = false)
    private String prioridadInstantanea;

    @Column(name = "base_priorizacion", length = 40, updatable = false)
    private String basePriorizacion;

    // Procedencia escalar opcional: no añade relación, cascada ni FK implícita.
    @Column(name = "huella_origen_id", updatable = false)
    private Long huellaOrigenId;

    @Column(name = "modelo_generacion", length = 128, updatable = false)
    private String modeloGeneracion;

    /** Conserva la firma del constructor anterior a las instantáneas. */
    public UsuarioRecomendacionIA(Integer id, String estado, LocalDateTime fechaGeneracion,
                                  LocalDateTime fechaCompletado, Usuario usuario,
                                  RecomendacionIA recomendacionIA) {
        this.id = id;
        this.estado = estado;
        this.fechaGeneracion = fechaGeneracion;
        this.fechaCompletado = fechaCompletado;
        this.usuario = usuario;
        this.recomendacionIA = recomendacionIA;
    }
}
