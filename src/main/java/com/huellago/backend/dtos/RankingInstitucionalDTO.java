package com.huellago.backend.dtos;

import java.util.List;

public record RankingInstitucionalDTO(Long institucionId,
        List<Participante> participantes, Integer miPosicion,
        boolean gruposDisponibles, String mensaje) {
    public RankingInstitucionalDTO {
        participantes = List.copyOf(participantes);
    }
    public record Participante(int posicion, String nombreVisible, int ecoPuntos, int nivel) { }
}
