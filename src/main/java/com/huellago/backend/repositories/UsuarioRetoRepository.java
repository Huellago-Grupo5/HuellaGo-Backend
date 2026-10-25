package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.huellago.backend.entities.UsuarioReto;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.query.Param;
import com.huellago.backend.repositories.projections.ParticipacionReporteVista;

public interface UsuarioRetoRepository extends JpaRepository<UsuarioReto, Integer>, UsuarioRetoReporteRepository {
    @Query("select ur.reto.id as retoId, count(ur) as participantes, coalesce(sum(ur.progreso), 0) as sumaProgreso "
            + "from UsuarioReto ur where ur.reto.id in :ids group by ur.reto.id")
    List<ParticipacionReporteVista> resumirParaReporte(@Param("ids") List<Integer> ids);

    boolean existsByUsuario_IdAndReto_Id(Long usuarioId, Integer retoId);

    List<UsuarioReto> findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaAceptacionDesc(
            Long usuarioId, String estado);

    Optional<UsuarioReto> findByIdAndUsuario_Id(Integer id, Long usuarioId);
    List<UsuarioReto> findByReto_Id(Integer retoId);

    @Query(value = "SELECT reto_id FROM usuarios_retos WHERE id = :id AND usuario_id = :usuarioId",
            nativeQuery = true)
    Optional<Integer> buscarRetoIdDeParticipacion(@Param("id") Integer id,
                                                  @Param("usuarioId") Long usuarioId);

    @Query(value = "SELECT id FROM usuarios_retos WHERE id = :id AND usuario_id = :usuarioId FOR UPDATE",
            nativeQuery = true)
    Optional<Integer> bloquearParticipacion(@Param("id") Integer id, @Param("usuarioId") Long usuarioId);

    @Query("SELECT COUNT(ur) FROM UsuarioReto ur WHERE ur.usuario.id = :usuarioId AND LOWER(ur.estado) = 'activo'")
    public Long contarRetosActivosUsuario(Long usuarioId);

    @Query(value = "SELECT COUNT(*) FROM usuarios_retos WHERE usuario_id = :usuarioId AND LOWER(estado) = 'completado'", nativeQuery = true)
    public Long contarRetosCompletadosUsuario_SQL(Long usuarioId);
}
