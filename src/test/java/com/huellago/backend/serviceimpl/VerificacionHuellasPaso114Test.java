package com.huellago.backend.serviceimpl;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifica el mecanismo canónico antes de registrar la decisión limitada a pruebas. Sin permisos productivos. */
class VerificacionHuellasPaso114Test {
    static final String ID = "TR_PLANIFICAR_DESPLAZAMIENTOS";
    static final Map<String,String> HUELLAS = Map.of(
            "AGRUPACION", "68ac3e60bc94150cdad8734a486f71094eaca769b7fd2e000ef2280485513977",
            "NECESIDAD", "5a63fa0c2cafe7e0c728618efc6fb5ba4c7519bf66995ff84bc10e3d12b3dae8",
            "SIN_ENFOQUE", "3b650417e025042186bfa88eac1df05a0d67f417809c3bafa6438f65fe804440");
    @Test void recalcularExactamenteLasTresHuellasAntesDeContinuar() throws Exception {
        var presentador = new PresentacionRecomendacionesIAServiceImpl();
        var politica = new PoliticaAutorizacionEditorialIAServiceImpl();
        var familia = presentador.definicionesParaRevision().stream().filter(d -> ID.equals(d.accionId())).toList();
        assertEquals(3,familia.size());
        for(var d:familia) {
            assertEquals("1",d.versionAccion()); assertEquals("1",d.versionPresentacion());
            String calculada=politica.huellaDefinicion(d);
            System.out.println(d.enfoque()+": "+calculada);
            assertEquals(HUELLAS.get(d.enfoque()),calculada,"DETENER: definición distinta de la autorizada");
        }
        Path dir=Path.of("target/paso114"); Files.createDirectories(dir);
        Path registro=dir.resolve("decision-editorial-prueba.md");
        if(!Files.exists(registro)) {
            StringBuilder s=new StringBuilder("# Decisión editorial limitada a pruebas internas\n\n")
                    .append("Registro realizado: ").append(ZonedDateTime.now(ZoneId.of("America/Lima")))
                    .append("\n\nAcción: TR_PLANIFICAR_DESPLAZAMIENTOS / 1. Alcance: APTA_PARA_PRUEBA_INTERNA.\n\n")
                    .append("Origen: decisión comunicada por el responsable del proyecto para continuar las pruebas de HuellaGo, PASO 114. ")
                    .append("No se atribuye firma digital, revisión del profesor ni acreditación profesional.\n\n")
                    .append("Las tres huellas fueron recalculadas mediante huellaDefinicion() y coincidieron exactamente antes de registrar esta decisión.\n\n")
                    .append("| Acción | Versión acción | Enfoque | Versión presentación | SHA-256 autorizado y comprobado |\n|---|---|---|---|---|\n");
            for(String enfoque:new TreeSet<>(HUELLAS.keySet())) s.append("| ").append(ID).append(" | 1 | ")
                    .append(enfoque).append(" | 1 | ").append(HUELLAS.get(enfoque)).append(" |\n");
            s.append("\nCampos variables: /transporte/medio, /transporte/kmSemana y /transporte/diasSemana. ")
                    .append("La huella vincula también los textos base y estos campos; no contiene hábitos individuales.\n\n")
                    .append("## Límites\n\nSolo fixture de src/test y contextos sintéticos. No habilita endpoints, inferencias reales en este paso, ")
                    .append("escritura de instantáneas, usuarios reales ni publicación. No existe aprobación científica externa ni autorización de publicación. ")
                    .append("Las acciones y presentaciones mantienen BORRADOR, PENDIENTE_REVISION y publicable=false. ")
                    .append("El constructor predeterminado productivo conserva cero autorizaciones. Este documento no es un registro de producción.\n\n")
                    .append("## Otras nueve familias excluidas\n\n");
            new CatalogoAccionesAmbientalesServiceImpl().listarBorradores().stream()
                    .filter(c -> !ID.equals(c.definicion().id())).forEach(c -> s.append("- ").append(c.definicion().id())
                            .append(" / ").append(c.definicion().version()).append("\n"));
            Files.writeString(registro,s.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        }
    }
}
