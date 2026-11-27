package com.huellago.backend.repositories;

import com.huellago.backend.entities.RolInstitucional;
import com.huellago.backend.entities.UsuarioInstitucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface UsuarioInstitucionRepository extends JpaRepository<UsuarioInstitucion, Long> {
    List<UsuarioInstitucion> findByUsuario_IdOrderByIdAsc(Long usuarioId);
    interface FilaRankingInstitucional {
        Long getUsuarioId();
        String getNombre();
        Integer getEcoPuntos();
        Integer getNivel();
    }

    @Query("select u.id as usuarioId, u.nombres as nombre, u.ecoPuntos as ecoPuntos, "
            + "u.nivel as nivel from UsuarioInstitucion ui join ui.usuario u "
            + "where ui.institucion.id = :institucionId and u.visibilidadComunidad = true "
            + "and u.ecoPuntos is not null and u.nivel is not null "
            + "order by u.ecoPuntos desc, u.id asc")
    List<FilaRankingInstitucional> listarRanking(@Param("institucionId") Long institucionId);

    long countByInstitucion_Id(Long institucionId);
    long countByGrupo_Id(Long grupoId);
    Optional<UsuarioInstitucion> findByUsuario_IdAndInstitucion_Id(Long usuarioId, Long institucionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ui from UsuarioInstitucion ui where ui.usuario.id = :usuarioId and ui.institucion.id = :institucionId")
    Optional<UsuarioInstitucion> bloquearParaGrupo(@Param("usuarioId") Long usuarioId,
                                                   @Param("institucionId") Long institucionId);
    interface FilaGrupoVisible {
        Long getGrupoId();
        Integer getEcoPuntos();
    }
    @Query("select ui.grupo.id as grupoId, u.ecoPuntos as ecoPuntos "
            + "from UsuarioInstitucion ui join ui.usuario u where ui.institucion.id = :institucionId "
            + "and ui.grupo.institucion.id = :institucionId "
            + "and u.visibilidadComunidad = true and u.ecoPuntos is not null")
    List<FilaGrupoVisible> listarMiembrosVisiblesParaRankingGrupos(@Param("institucionId") Long institucionId);
    boolean existsByUsuario_IdAndInstitucion_Id(Long usuarioId, Long institucionId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select ui from UsuarioInstitucion ui where ui.usuario.id = :usuarioId and ui.institucion.id = :institucionId")
    Optional<UsuarioInstitucion> bloquearVinculo(@Param("usuarioId") Long usuarioId,
                                                @Param("institucionId") Long institucionId);
    boolean existsByUsuario_IdAndInstitucion_PaisAndInstitucion_IdentificadorFiscal(
            Long usuarioId, String pais, String identificadorFiscal);
    boolean existsByUsuario_IdAndInstitucion_IdAndRol(
            Long usuarioId, Long institucionId, RolInstitucional rol);
}
