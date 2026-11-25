package com.huellago.backend.repositories;

import com.huellago.backend.entities.HuellaCarbono;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Analiza las consultas derivadas sin datasource; no certifica ejecución PostgreSQL. */
class HuellaConsultaFechaRepositoryTest {
    @Test void consultasDerivadasFiltranPropietarioYLimitesYOrdenanPorFechaEId() {
        var nombres = List.of(
                "findTopByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc",
                "findByUsuario_IdAndFechaCalculoGreaterThanEqualAndFechaCalculoLessThanOrderByFechaCalculoDescIdDesc");
        for (String nombre : nombres) {
            assertTrue(java.util.Arrays.stream(HuellaCarbonoRepository.class.getMethods())
                    .anyMatch(m -> m.getName().equals(nombre)));
            var consulta = new PartTree(nombre, HuellaCarbono.class);
            var partes = consulta.getParts().stream().toList();
            assertEquals(List.of("usuario.id", "fechaCalculo", "fechaCalculo"),
                    partes.stream().map(p -> p.getProperty().toDotPath()).toList());
            assertEquals(List.of(Part.Type.SIMPLE_PROPERTY, Part.Type.GREATER_THAN_EQUAL, Part.Type.LESS_THAN),
                    partes.stream().map(Part::getType).toList());
            assertEquals(Sort.by(Sort.Order.desc("fechaCalculo"), Sort.Order.desc("id")), consulta.getSort());
            assertEquals(nombre.startsWith("findTop"), consulta.isLimiting());
            if (consulta.isLimiting()) assertEquals(1, consulta.getMaxResults());
        }
    }
}
