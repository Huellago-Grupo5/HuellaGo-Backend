package com.huellago.backend.entities;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "objetivos_ambientales") @Data @NoArgsConstructor @AllArgsConstructor
public class ObjetivoAmbiental {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String nombre;
    private String descripcion;
}
