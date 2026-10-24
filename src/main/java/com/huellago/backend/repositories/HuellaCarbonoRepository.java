package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.huellago.backend.entities.HuellaCarbono;

import java.util.List;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public interface HuellaCarbonoRepository extends JpaRepository<HuellaCarbono, Long> {

    public HuellaCarbono findTopByUsuario_IdOrderByFechaCalculoDesc(Long usuarioId);

    public HuellaCarbono findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(Long usuarioId);

    public List<HuellaCarbono> findByUsuario_IdOrderByFechaCalculoDesc(Long usuarioId);

    public List<HuellaCarbono> findByUsuario_IdOrderByFechaCalculoDescIdDesc(Long usuarioId);

    public HuellaCarbono findTopByUsuario_IdAndFechaCalculoBetweenOrderByFechaCalculoDescIdDesc(
            Long usuarioId, LocalDateTime inicio, LocalDateTime fin);

    @Query("SELECT COUNT(h) FROM HuellaCarbono h WHERE h.usuario.id = :usuarioId")
    public Long contarHuellasUsuario(Long usuarioId);

    @Query(value = "SELECT co2_total FROM huellas_carbono WHERE usuario_id = :usuarioId ORDER BY fecha_calculo DESC NULLS LAST, id DESC LIMIT 1", nativeQuery = true)
    public java.math.BigDecimal buscarHuellaActual_SQL(Long usuarioId);

    @Query(value = """
            SELECT ultima.co2_total
            FROM (
                SELECT DISTINCT ON (usuario_id)
                       usuario_id, co2_total
                FROM huellas_carbono
                ORDER BY usuario_id, fecha_calculo DESC NULLS LAST, id DESC
            ) ultima
            WHERE ultima.co2_total IS NOT NULL
              AND ultima.co2_total >= 0
            """, nativeQuery = true)
    List<BigDecimal> buscarUltimasHuellasValidasPorUsuario();
}
