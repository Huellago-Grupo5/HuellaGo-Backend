package com.huellago.backend.dtos;

import java.util.List;

public record RankingComunitarioDTO(
        List<Participante> participantes,
        Integer miPosicion,
        String mensaje) {

    public RankingComunitarioDTO {
        participantes = List.copyOf(participantes);
    }

    public record Participante(int posicion, String nombreVisible, int ecoPuntos, int nivel) { }
}
