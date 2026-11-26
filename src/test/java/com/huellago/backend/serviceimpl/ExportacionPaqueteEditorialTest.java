package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.DefinicionPresentacionIADTO;
import com.huellago.backend.dtos.RecomendacionFundamentadaIADTO.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Exportación explícita de API canónica. Sin contextos personales, inferencia, Spring ni DataSource.
 * CREATE_NEW protege futuras decisiones humanas contra sobrescritura. No crea autorizaciones. */
@EnabledIfEnvironmentVariable(named="HUELLAGO_EXPORTAR_PAQUETE_EDITORIAL", matches="true")
class ExportacionPaqueteEditorialTest {
    private final CatalogoAccionesAmbientalesServiceImpl catalogo = new CatalogoAccionesAmbientalesServiceImpl();
    private final PresentacionRecomendacionesIAServiceImpl presentador = new PresentacionRecomendacionesIAServiceImpl();
    private final PoliticaAutorizacionEditorialIAServiceImpl politica = new PoliticaAutorizacionEditorialIAServiceImpl();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private static final List<String> COLUMNAS = List.of("accionId","versionAccion","enfoque","versionPresentacion",
            "titulo","descripcion","motivo","camposContextoPermitidos","observacionesPreliminares",
            "estadoRevisionHumana","decisionHumana","responsableRevision","fechaRevision","comentariosRevision");
    private record Observacion(String clasificacion, String detalle) { }

    @Test void exportarTreintaDefinicionesExactasYComprobarCsv() throws Exception {
        Path dir = Path.of("target/paso113"); Files.createDirectories(dir);
        var acciones = catalogo.listarBorradores(); var defs = presentador.definicionesParaRevision();
        String antes = mapper.writeValueAsString(acciones), antesDefs = mapper.writeValueAsString(defs);
        assertEquals(10,acciones.size()); assertEquals(30,defs.size());
        Set<String> identidades = new HashSet<>(); Set<String> usados = new TreeSet<>();
        Map<String,Integer> conteos = new TreeMap<>(); List<List<String>> filas = new ArrayList<>(); filas.add(COLUMNAS);
        StringBuilder completo = new StringBuilder("# Catálogo editorial canónico de HuellaGo\n\n")
                .append("10 familias y 30 presentaciones. Estado BORRADOR, revisión PENDIENTE_REVISION y publicable=false. ")
                .append("Ningún contenido está aprobado. Los bloques de texto contienen los originales, sin sustitución de variables.\n\n")
                .append("Los dos enfoques se numeran alfabéticamente solo para organización: el catálogo usa un conjunto sin prioridad. ")
                .append("El nombre mostrado de cada familia es su título canónico SIN_ENFOQUE, no un campo adicional del catálogo.\n\n");
        StringBuilder integridad = new StringBuilder("# Comprobación de integridad\n\n")
                .append("Extracción por definicionesParaRevision(), sin reflexión sobre mapas privados ni composición con hábitos.\n\n")
                .append("Huellas obtenidas exclusivamente mediante PoliticaAutorizacionEditorialIAServiceImpl.huellaDefinicion(). ")
                .append("Son identificadores de integridad, no autorizaciones ni firmas de un revisor.\n\n")
                .append("| accionId | versionAccion | enfoque | versionPresentacion | SHA-256 | Campos autorizados sin marcador literal |\n")
                .append("|---|---|---|---|---|---|\n");
        StringBuilder observaciones = new StringBuilder("# Revisión técnica preliminar\n\n")
                .append("No es aprobación editorial ni certificación ambiental. Todas las decisiones humanas siguen PENDIENTE. ")
                .append("Responsable, fecha y comentarios humanos se dejan vacíos en el CSV.\n\n")
                .append("## Criterios y límites\n\n")
                .append("Se inspeccionaron títulos, pasos, motivos, condiciones, restricciones y variables. No se buscaron ni inventaron fuentes externas. ")
                .append("No se identificaron promesas cuantificadas de CO2 ni diagnósticos de consumo alto/bajo. ")
                .append("El consumo eléctrico conserva kWh/día del hogar, sin dividir por habitantes ni presentarlo necesariamente como medición.\n\n")
                .append("La disponibilidad de alternativas, servicios de reciclaje, alimentos, envases y materiales no se conoce: ")
                .append("los pasos solicitan comprobarla, no afirman que exista. Esta dependencia no constituye por sí sola un defecto. ")
                .append("No se verificó utilidad ambiental causal, efectos netos ni adecuación local en Perú. Cualquier afirmación futura de ahorro o superioridad ambiental necesitará fuentes y metodología revisadas.\n\n")
                .append("| accionId | enfoque | Clasificación técnica preliminar | Observación |\n|---|---|---|---|\n");
        for (var candidata : acciones) {
            var accion = candidata.definicion();
            var familia = defs.stream().filter(d -> d.accionId().equals(accion.id()) && d.versionAccion().equals(accion.version())).toList();
            assertEquals(3,familia.size());
            Set<String> enfoques = new TreeSet<>();
            accion.parametros().get("enfoque").valoresPermitidos().forEach(v -> enfoques.add(((Texto)v).valor()));
            assertEquals(2,enfoques.size()); var esperados = new HashSet<>(enfoques); esperados.add("SIN_ENFOQUE");
            assertEquals(esperados,new HashSet<>(familia.stream().map(DefinicionPresentacionIADTO::enfoque).toList()));
            var sin = familia.stream().filter(d -> d.enfoque().equals("SIN_ENFOQUE")).findFirst().orElseThrow();
            completo.append("## ").append(accion.id()).append(" / ").append(accion.version()).append("\n\n")
                    .append("Nombre mostrado: ").append(sin.titulo()).append("\n\nCategoría: ").append(accion.categoria()).append("\n\nObjetivo original:\n\n");
            bloque(completo,candidata.objetivo());
            completo.append("Descripción candidata del catálogo (distinta de las presentaciones):\n\n"); bloque(completo,candidata.descripcion());
            completo.append("Condiciones canónicas tipadas (todas deben cumplirse):\n\n");
            Map<String,List<Object>> condiciones = new TreeMap<>();
            accion.condiciones().forEach((ruta, valores) -> condiciones.put(ruta,valores.stream()
                    .map(v -> (Object)(v instanceof Texto t ? t.valor() : v instanceof Booleano b ? b.valor() : ((Numero)v).valor()))
                    .sorted(Comparator.comparing(Object::toString)).toList()));
            bloque(completo,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(condiciones));
            completo.append("Además: categoría completa y válida, evidencias requeridas y acción no realizada según id y versión.\n\n")
                    .append("Evidencias requeridas: ").append(new TreeSet<>(accion.evidenciasRequeridas())).append("\n\n")
                    .append("Enfoque 1 y enfoque 2 (orden alfabético): ").append(String.join(", ",enfoques)).append("\n\n")
                    .append("Información adicional permitida: ").append(new TreeSet<>(accion.informacionAdicionalPermitida())).append("\n\n")
                    .append("Restricciones originales:\n\n");
            for(String restriccion:candidata.restricciones()) bloque(completo,restriccion);
            List<String> orden = new ArrayList<>(enfoques); orden.add("SIN_ENFOQUE");
            for(String enfoque:orden) {
                var d=familia.stream().filter(v -> v.enfoque().equals(enfoque)).findFirst().orElseThrow();
                String identidad=d.accionId()+"/"+d.versionAccion()+"/"+d.enfoque()+"/"+d.versionPresentacion();
                assertTrue(identidades.add(identidad)); assertEquals("1",d.versionAccion()); assertEquals("1",d.versionPresentacion());
                var variables=variables(d); assertTrue(d.camposContextoPermitidos().containsAll(variables)); usados.addAll(variables);
                Set<String> noLiterales=new TreeSet<>(d.camposContextoPermitidos()); noLiterales.removeAll(variables);
                var observacion=observacion(d); conteos.merge(observacion.clasificacion(),1,Integer::sum);
                String hash=politica.huellaDefinicion(d); assertTrue(hash.matches("[0-9a-f]{64}"));
                completo.append("### ").append(enfoque).append("\n\nIdentidad: ").append(identidad)
                        .append("\n\nEstado: BORRADOR / PENDIENTE_REVISION / publicable=false\n\nTítulo exacto:\n\n");
                bloque(completo,d.titulo()); completo.append("Descripción exacta:\n\n"); bloque(completo,d.descripcion());
                completo.append("Motivo exacto:\n\n"); bloque(completo,d.motivo());
                completo.append("Campos autorizados: ").append(new TreeSet<>(d.camposContextoPermitidos()))
                        .append("\n\nMarcadores presentes: ").append(variables).append("\n\nSHA-256 canónico: `").append(hash).append("`\n\n");
                integridad.append("| ").append(d.accionId()).append(" | ").append(d.versionAccion()).append(" | ").append(enfoque)
                        .append(" | ").append(d.versionPresentacion()).append(" | ").append(hash).append(" | ").append(noLiterales).append(" |\n");
                observaciones.append("| ").append(d.accionId()).append(" | ").append(enfoque).append(" | ")
                        .append(observacion.clasificacion()).append(" | ").append(observacion.detalle()).append(" |\n");
                filas.add(List.of(d.accionId(),d.versionAccion(),enfoque,d.versionPresentacion(),d.titulo(),d.descripcion(),d.motivo(),
                        mapper.writeValueAsString(new TreeSet<>(d.camposContextoPermitidos())),
                        observacion.clasificacion()+": "+observacion.detalle(),"PENDIENTE","PENDIENTE","","",""));
            }
        }
        assertEquals(30,identidades.size()); assertEquals(31,filas.size());
        observaciones.append("\n## Conteos\n\n").append(conteos).append("\n\n")
                .append("Todas las 30 variantes requieren revisión humana, incluidas las clasificadas SIN_OBSERVACIONES_PRELIMINARES. ")
                .append("POSIBLE_PROBLEMA: 0. No se identificó una contradicción factual clara ni una instrucción insegura concreta en esta revisión.\n\n")
                .append("## Recomendaciones de revisión\n\n")
                .append("1. Distinguir verificación del registro energético de reducción ambiental conseguida.\n")
                .append("2. Revisar si la organización de movilidad activa tiene suficiente utilidad ambiental sin sugerir que reducirla sea un beneficio.\n")
                .append("3. Revisar el título de entrega de reciclables: el paso es condicional, pero el título puede sugerir una entrega ya prevista.\n")
                .append("4. Evaluar legibilidad del paso largo de reutilización sin retirar las precauciones de higiene y seguridad.\n")
                .append("5. No aprobar por similitud de títulos ni por BUILD SUCCESS. Revisar las tres variantes exactas de cada familia. ")
                .append("El CSV no se importa automáticamente como registro de permisos.\n\n")
                .append("La semántica actual de la plantilla se preservó íntegramente. No se implementaron correcciones ni autorizaciones.\n");
        completo.append("## Variables y procedencia\n\n").append(procedencia());
        integridad.append("\n## Resultado\n\n10 familias; 30 variantes; 0 faltantes; 0 identidades duplicadas; versiones de acción y presentación 1. ")
                .append("Cada familia coincide con los dos enfoques de su catálogo y SIN_ENFOQUE. Todos los marcadores están autorizados.\n\n")
                .append("Los textos se recuperaron directamente de la API y se verificaron tras leer el CSV. ")
                .append("Las huellas se calcularon con la política existente, sin algoritmo alternativo.\n\n")
                .append("Los campos autorizados que no aparecen como marcador literal no son variables inventadas: ")
                .append("por ejemplo, /residuos/reciclas en RE_REVISAR_ENTREGA respalda el motivo fijo bajo su condición true.\n\n")
                .append("CSV: UTF-8 con BOM, separador coma, registros CRLF y todas las celdas entre comillas. ")
                .append("Comillas internas duplicadas; 14 columnas y 30 registros. En Excel, importar como UTF-8/coma y conservar versiones como texto. ")
                .append("No se añadieron fórmulas ni conversiones a valores personalizados.\n\n")
                .append("SHA-256 vincula contenido, identidad, versiones, enfoque y campos; no acredita autor ni aprobación. ")
                .append("No modificar la matriz original sin conservar una copia: una edición de texto requiere nueva revisión y huella.\n\n")
                .append("## Variables y procedencia\n\n").append(procedencia());
        Map<String,String> salidas=new LinkedHashMap<>();
        salidas.put("catalogo-editorial-completo.md",completo.toString());
        salidas.put("matriz-revision-editorial.csv",csv(filas));
        salidas.put("informe-observaciones.md",observaciones.toString());
        salidas.put("informe-integridad.md",integridad.toString());
        for(var salida:salidas.entrySet()) assertFalse(Files.exists(dir.resolve(salida.getKey())),"No sobrescribir revisión humana");
        for(var salida:salidas.entrySet()) Files.writeString(dir.resolve(salida.getKey()),salida.getValue(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        var recuperadas=leerCsv(Files.readString(dir.resolve("matriz-revision-editorial.csv"),StandardCharsets.UTF_8));
        assertEquals(filas,recuperadas); // Comparación exacta: tildes, espacios, variables, comillas y estados.
        String documento=Files.readString(dir.resolve("catalogo-editorial-completo.md"),StandardCharsets.UTF_8);
        for(var d:defs) for(String texto:List.of(d.titulo(),d.descripcion(),d.motivo())) assertTrue(documento.contains("```text\n"+texto+"\n```"));
        for(var fila:recuperadas.subList(1,recuperadas.size())) {
            assertEquals(14,fila.size()); assertEquals("PENDIENTE",fila.get(9)); assertEquals("PENDIENTE",fila.get(10));
            assertEquals(List.of("","",""),fila.subList(11,14));
        }
        assertEquals(antes,mapper.writeValueAsString(catalogo.listarBorradores()));
        assertEquals(antesDefs,mapper.writeValueAsString(presentador.definicionesParaRevision()));
        System.out.println("Exportación canónica: 10 familias, 30 variantes, CSV 30 x 14, " + conteos + ", decisiones humanas PENDIENTE");
    }
    private void bloque(StringBuilder s,String texto) { s.append("```text\n").append(texto).append("\n```\n\n"); }
    private Set<String> variables(DefinicionPresentacionIADTO d) {
        Set<String> rutas=new TreeSet<>(); var patron=Pattern.compile("\\{(/[^{}]+)\\}");
        for(String texto:List.of(d.titulo(),d.descripcion(),d.motivo())) { var m=patron.matcher(texto); while(m.find()) rutas.add(m.group(1)); }
        return rutas;
    }
    private Observacion observacion(DefinicionPresentacionIADTO d) {
        return switch(d.accionId()) {
            case "EN_REVISAR_REGISTRO_HOGAR" -> new Observacion("REQUIERE_REVISION","Acción de calidad de datos; confirmar su ubicación como recomendación ambiental sin presentar una reducción conseguida. Reconoce medición o estimación y ámbito hogar.");
            case "TR_REVISAR_MOVILIDAD_ACTIVA" -> new Observacion("REQUIERE_REVISION","Utilidad ambiental indirecta por justificar; no interpretar organizar o reducir desplazamientos activos como ahorro demostrado. El paso respeta la movilidad registrada.");
            case "RE_REVISAR_ENTREGA" -> new Observacion("REQUIERE_REVISION","El título habla de tu entrega, aunque reciclar no acredita una entrega prevista. Descripción y motivo sí conservan la condición y el desconocimiento de la vía.");
            case "RE_REVISAR_REUTILIZACION" -> d.enfoque().equals("SIN_ENFOQUE")
                    ? new Observacion("REQUIERE_REVISION","Paso extenso con varias condiciones; revisar legibilidad manteniendo higiene, adecuación y seguridad. No obliga a comprar.")
                    : new Observacion("SIN_OBSERVACIONES_PRELIMINARES","Condiciona existencia o viabilidad del uso y conserva seguridad; no inventa objetos ni cantidades.");
            case "TR_PLANIFICAR_DESPLAZAMIENTOS" -> new Observacion("SIN_OBSERVACIONES_PRELIMINARES","Solicita comprobar necesidades reales; no presupone gestiones combinables, destinos u horarios ni promete ahorros.");
            case "TR_EXPLORAR_ALTERNATIVAS_AUTO" -> new Observacion("SIN_OBSERVACIONES_PRELIMINARES","Alternativas planteadas como comprobación; no garantiza disponibilidad, seguridad, costos ni reducción de emisiones.");
            case "EN_REVISAR_USOS" -> new Observacion("SIN_OBSERVACIONES_PRELIMINARES","Se refiere a la fuente registrada y a usos por identificar; no inventa equipos, consumos elevados o desconexiones necesarias.");
            case "AL_PLANIFICAR_APROVECHAMIENTO" -> new Observacion("SIN_OBSERVACIONES_PRELIMINARES","Pide comprobar existencias respetando el tipo alimentario; no atribuye alimentos ni desperdicios existentes.");
            case "AL_REVISAR_PRESENTACION" -> new Observacion("SIN_OBSERVACIONES_PRELIMINARES","Opciones condicionales compatibles con la dieta; no equipara menos envase con menor impacto ni garantiza disponibilidad.");
            case "RE_INICIAR_SEPARACION" -> new Observacion("SIN_OBSERVACIONES_PRELIMINARES","Comprueba vías y materiales antes de actuar; no garantiza servicio o recuperación efectiva ni presupone ausencia de toda separación.");
            default -> throw new IllegalStateException("Familia sin revisión preliminar");
        };
    }
    private String procedencia() {
        return "| Ruta | Procedencia y significado |\n|---|---|\n"
                +"| /transporte/medio | Contexto Transporte.medio, cualitativo normalizado desde Habito.unidad |\n"
                +"| /transporte/kmSemana | Transporte.kmSemana, Habito.valor en km/semana |\n"
                +"| /transporte/diasSemana | Transporte.diasSemana, Habito.valor entero de días/semana |\n"
                +"| /energia/vivienda | Energía.vivienda, Habito.unidad cualitativa |\n"
                +"| /energia/personas | Energía.personas, Habito.valor entero |\n"
                +"| /energia/fuente | Energía.fuente, Habito.unidad cualitativa; Electricidad no acredita origen renovable o fósil |\n"
                +"| /energia/consumoKwhDiario | Energía.consumoKwhDiario, opcional, Habito.valor; kWh/día del HOGAR, no por persona |\n"
                +"| /alimentacion/tipo | Alimentación.tipo, Habito.unidad cualitativa |\n"
                +"| /residuos/plasticos | Residuos.plasticos, Habito.unidad cualitativa Bajo/Medio/Alto; no cantidad |\n"
                +"| /residuos/reciclas | Residuos.reciclas, booleano validado desde Habito.valor; en composición se muestra sí/no |\n\n"
                +"Los marcadores literales conservan el formato {/categoria/campo}. La exportación no recuperó hábitos ni sustituyó valores.\n";
    }
    private String csv(List<List<String>> filas) {
        StringBuilder s=new StringBuilder("\uFEFF");
        for(var fila:filas) s.append(fila.stream().map(v -> "\""+v.replace("\"","\"\"")+"\"").collect(java.util.stream.Collectors.joining(","))).append("\r\n");
        return s.toString();
    }
    private List<List<String>> leerCsv(String texto) {
        assertTrue(texto.startsWith("\uFEFF")); List<List<String>> filas=new ArrayList<>(); List<String> fila=new ArrayList<>();
        StringBuilder celda=new StringBuilder(); boolean comillas=false;
        for(int i=1;i<texto.length();i++) {
            char c=texto.charAt(i);
            if(c=='"') { if(comillas && i+1<texto.length() && texto.charAt(i+1)=='"') { celda.append('"'); i++; } else comillas=!comillas; }
            else if(c==',' && !comillas) { fila.add(celda.toString()); celda.setLength(0); }
            else if(c=='\r' && !comillas && i+1<texto.length() && texto.charAt(i+1)=='\n') {
                fila.add(celda.toString()); celda.setLength(0); filas.add(List.copyOf(fila)); fila.clear(); i++;
            } else celda.append(c);
        }
        assertFalse(comillas); assertTrue(fila.isEmpty()); assertEquals(0,celda.length()); return filas;
    }
}
