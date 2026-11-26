package com.huellago.backend.dtos;

import com.huellago.backend.entities.TipoGrupoInstitucional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class GrupoInstitucionalDTO {
    private GrupoInstitucionalDTO() { }
    public record Datos(@NotNull TipoGrupoInstitucional tipo,
            @NotBlank @Size(max = 100) String nombre) { }
    public record Grupo(Long id, Long institucionId, TipoGrupoInstitucional tipo, String nombre) { }
    public record Invitacion(String codigo, Long institucionId, java.time.LocalDateTime venceEl) { }
    public record Canje(@NotBlank String codigo) { }
    public record Afiliacion(Long usuarioId, Long institucionId, Long grupoId) { }
    public record FilaRanking(int posicion, Long grupoId, TipoGrupoInstitucional tipo,
            String nombre, long ecoPuntos, int participantesVisibles) { }
    public record Ranking(Long institucionId, List<FilaRanking> grupos, String mensaje) {
        public Ranking { grupos = List.copyOf(grupos); }
    }
}
