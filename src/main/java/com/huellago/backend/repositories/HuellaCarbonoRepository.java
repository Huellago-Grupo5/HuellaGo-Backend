package com.huellago.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.huellago.backend.entities.HuellaCarbono;

import java.util.List;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import org.springframework.data.repository.query.Param;

public interface HuellaCarbonoRepository extends JpaRepository<HuellaCarbono, Long> {
    interface FilaInstitucional {
        Long getUsuarioId();
        BigDecimal getCo2Total();
        BigDecimal getCo2Transporte();
        BigDecimal getCo2Energia();
        BigDecimal getCo2Alimentacion();
        BigDecimal getCo2Residuos();
        LocalDateTime getFechaCalculo();
        String getMetodologiaVersion();
        String getPeriodoCalculo();
        String getUnidadCalculo();
        String getPerimetroCalculo();
        String getFactoresAplicados();
    }

    @Query("select h.usuario.id as usuarioId, h.co2Total as co2Total, "
            + "h.co2Transporte as co2Transporte, h.co2Energia as co2Energia, "
            + "h.co2Alimentacion as co2Alimentacion, h.co2Residuos as co2Residuos, "
            + "h.fechaCalculo as fechaCalculo, h.metodologiaVersion as metodologiaVersion, "
            + "h.periodoCalculo as periodoCalculo, h.unidadCalculo as unidadCalculo, "
            + "h.perimetroCalculo as perimetroCalculo, h.factoresAplicados as factoresAplicados "
            + "from HuellaCarbono h, UsuarioInstitucion ui "
            + "where ui.usuario.id = h.usuario.id and ui.institucion.id = :institucionId "
            + "order by h.usuario.id asc, case when h.fechaCalculo is null then 1 else 0 end asc, "
            + "h.fechaCalculo desc, h.id desc")
    List<FilaInstitucional> historialInstitucional(@Param("institucionId") Long institucionId);

    public HuellaCarbono findTopByUsuario_IdOrderByFechaCalculoDesc(Long usuarioId);

    public HuellaCarbono findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(Long usuarioId);

    public List<HuellaCarbono> findByUsuario_IdOrderByFechaCalculoDesc(Long usuarioId);

    public List<HuellaCarbono> findByUsuario_IdOrderByFechaCalculoDescIdDesc(Long usuarioId);

    public HuellaCarbono findTopByUsuario_IdAndFechaCalculoBetweenOrderByFechaCalculoDescIdDesc(
            Long usuarioId, LocalDateTime inicio, LocalDateTime fin);

    HuellaCarbono findTopByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
            Long usuarioId, LocalDateTime inicio, LocalDateTime fin);

    List<HuellaCarbono> findByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc(
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
