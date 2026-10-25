package com.huellago.backend.events;

import java.util.List;

/** Identidades obtenidas de operaciones ambientales ya autorizadas, nunca de un body de refresco. */
public record InformacionAmbientalActualizada(List<Long> usuarioIds) {
    public InformacionAmbientalActualizada {
        usuarioIds = usuarioIds.stream().distinct().sorted().toList();
    }

    public InformacionAmbientalActualizada(Long usuarioId) {
        this(List.of(usuarioId));
    }
}
