# PASO 135 — Integración de información ambiental vigente

Fecha: 2026-11-24, America/Lima. Rama verificada: R-Carlos. Implementación puntual sobre los componentes existentes. No se activa publicación ni se acredita metodología científica.

## 1. Alcance implementado y límite metodológico

El contexto IA puede incorporar ahora la última huella propia, sus componentes disponibles, estado de vigencia y limitaciones. La captura protege ese resumen; V2 lo recibe separado de evidencias. Cuando la huella no es utilizable, la selección sigue utilizando los hábitos válidos y las acciones compatibles/autorizadas; no se invoca el generador determinista como respaldo.

**No se identifica un foco personal dominante con las filas actuales.** `HuellaCarbono` no conserva método/factor/perímetro de cada cálculo histórico. La revisión de PASO133 ya identificó falta de fundamento para comparar categorías únicamente por compartir unidad. Se implementa esa protección, sin inventar una acreditación o una bandera que haga comparables los valores.

El resumen ofrece `focos()` vacío: no existe en el esquema actual información suficiente para acreditar un conjunto comparable. No es una omisión de cálculo por empate ni un cero artificial. La identificación efectiva de focos dentro de un conjunto justificadamente comparable sigue dependiendo de la decisión metodológica del responsable/profesor y de cómo acreditar su origen. No se afirma haber resuelto ese pendiente de HU31 CA2.

## 2. Archivos modificados

Rutas productivas bajo `src/main/java/com/huellago/backend/`:

| Archivo | Operaciones modificadas |
|---|---|
| `services/ContextoRecomendacionesIAService.java` | `Resultado` incorpora `ResumenHuella` opcional; constructor anterior conservado. Record anidado y enumeraciones de vigencia/comparabilidad/perímetro/limitaciones, sin otro servicio. |
| `serviceimpl/ContextoRecomendacionesIAServiceImpl.java` | Constructor adicional con repositorio de huella seleccionado por Spring mediante Autowired; `construir` añade resumen; nuevo método privado `resumen`. Normalización y selección de hábitos conservadas. |
| `serviceimpl/CapturaContextoAmbientalIAServiceImpl.java` | Versión de captura V2 y codificación canónica del resumen en `huella`, mediante método privado adicional. Lectura/comparación y propagaciones conservadas. |
| `serviceimpl/OllamaRecomendacionesServiceImpl.java` | `contextoDeResultado` conserva Resultado completo; V2 transporta resumen a `seleccionarV2` y al constructor común de solicitud `solicitudV2`. V1 sigue utilizando únicamente contexto de hábitos y reglas anteriores. |
| `serviceimpl/ContratoOllamaV2.java` | Sobrecarga de `datos` con resumen y texto adicional `usoResumen`. Esquema de decisiones sin cambios. |

Archivos existentes de pruebas/composición modificados:

- `src/test/java/com/huellago/backend/serviceimpl/AplicacionDemoIA127.java`: únicamente dependencia HuellaCarbonoRepository del bean constructor. Sin arranque ni cambio de controles del entorno.
- `src/test/java/com/huellago/backend/serviceimpl/EntradaDemoIA127.java`: advertencias comprensibles sobre estado de huella y perímetros, conservando diagnóstico134 y los campos públicos existentes.

Pruebas nuevas, bajo el mismo paquete de `src/test`:

- `HuellaContexto135Test.java`.
- `CapturaHuella135Test.java`.
- `PersistenciaHuella135Test.java`.
- `OllamaResumen135Test.java`.
- `ResumenRespuesta135Test.java`.

No se modificaron fixtures anteriores ni pruebas existentes. Tampoco fue necesario modificar coordinador manual, resultado manual, escritor, entidades o repositorios.

## 3. Recuperación y validación

`ContextoRecomendacionesIAServiceImpl` utiliza exactamente `HuellaCarbonoRepository.findTopByUsuario_IdOrderByFechaCalculoDescIdDesc(usuarioId)`. El ID lo proporciona el mecanismo existente de captura autenticada, que valida identidad/propiedad. La consulta se realiza dentro del constructor ambiental de lectura REQUIRED; no llama al servicio de cálculo ni publica eventos.

Se reutilizan los mismos cuatro componentes que expone `HuellaCarbonoServiceImpl.obtenerDesglose`: Transporte, Energía, Alimentación y Residuos. No se crea otra fórmula ni se calcula una reducción. La validación toma como antecedente `RecomendacionServiceImpl.huellaUtilizable`, sin modificarlo.

Comprobaciones:

- Propietario correcto e ID persistente positivo. Si falla, el resumen INVALIDA no traslada ID, fecha, total ni componentes de esa fila.
- Fecha de cálculo presente, total presente/no negativo y cuatro componentes presentes/no negativos.
- Los componentes ausentes no se convierten en cero. Los negativos se excluyen del mapa utilizable y se registra su invalidez; los demás se conservan como datos disponibles internos. Cero permanece como dato real.
- Vigencia respecto a la mayor fecha de registro/actualización de los hábitos seleccionados por las reglas actuales. No se vuelve a seleccionar o normalizar hábitos en otra lógica.
- No se exige que el total histórico sea igual a la suma de componentes. El redondeo independiente no invalida la fila.

Unidad y período son los del contrato del modelo: kg CO2e, semanal. No son columnas de la entidad: no se afirma haber verificado metadatos físicos por fila ni factores científicos almacenados. El origen se declara orientativo y sin trazabilidad metodológica histórica.

### Estados

| Estado | Condición |
|---|---|
| INEXISTENTE | Consulta sin fila; mapa vacío, sin referencia/fecha/total ficticios. |
| INVALIDA | Identidad/propietario, fecha, total o componentes inválidos/ausentes. No afecta la validez independiente de los hábitos. |
| OBSOLETA | Fila estructuralmente válida, pero algún hábito seleccionado es posterior al cálculo. |
| NO_VERIFICABLE | Fila estructuralmente válida, sin hábitos seleccionados o con fechas de hábitos desconocidas que impiden acreditar vigencia. |
| VIGENTE | Cumple las comprobaciones anteriores de propiedad, estructura y cronología. No significa comparabilidad ni certificación. |

Si hay invalidez y obsolescencia simultáneas prevalece INVALIDA, conservándose las limitaciones detectadas. Si existe un hábito posterior y otros sin fecha, prevalece OBSOLETA. No se inventa una fecha de caducidad basada en el reloj.

## 4. Resumen inmutable, contexto parcial y perímetros

`ResumenHuella` conserva referencia, fecha, total registrado disponible, mapa de componentes, vigencia, comparabilidad, mapa de perímetros y conjunto de limitaciones. Mapas/conjuntos tienen copias defensivas; BigDecimal/LocalDateTime son inmutables. Su toString omite valores. No se agregan logs de hábitos.

La información ambiental parcial sigue figurando en las categorías incompletas/observaciones de Resultado y como limitación del resumen. Un componente histórico de una categoría incompleta no la convierte en válida, ni en categoría de impacto bajo.

- Con consumo eléctrico explícito en el contexto actual, Energía se marca HOGAR_SEGUN_CONTEXTO y se conserva la advertencia de que no acredita la fórmula/atribución de la fila histórica.
- Los registros de otras categorías no acreditan automáticamente una atribución ambiental personal: su etiqueta es REGISTRO_USUARIO_NO_ACREDITADO.
- Cuando coexisten componente de Energía doméstica y otros componentes, el resumen marca PERIMETROS_NO_COMPARABLES. En otros casos la comparabilidad sigue NO_ACREDITADA por ausencia de metodología/perímetro trazables; no se aprueba automáticamente por faltar consumo eléctrico.
- No se divide entre habitantes, no se comparan km con kWh y no se equiparan emisiones de categoría con ahorro de acción. Ceros, empates y máximos numéricos no superan esta protección.

Constructor anterior de Resultado y constructor anterior del servicio permanecen disponibles. La construcción manual antigua de solo hábitos devuelve resumen NULL, no finge una consulta sin resultados. Spring utiliza el constructor con ambos repositorios; el lanzador académico127 adapta explícitamente esa dependencia. No se modificó la composición/fixture anterior123.

## 5. Captura y comprobación final

Nueva versión: `HUELLAGO_CONTEXTO_VIGENCIA_V2`; el bloque de resumen usa `RESUMEN_HUELLA_V1` dentro de la representación canónica.

La huella incluye presencia/ausencia del resumen, referencia, fecha, total disponible, unidad/período/origen, vigencia/comparabilidad, componentes, perímetros, focos y limitaciones. Mapas/conjuntos se ordenan; BigDecimal elimina diferencias de escala para el hash. No depende del orden de registros o de un timestamp aleatorio.

Capturar/revalidar siguen REQUIRES_NEW/REPEATABLE_READ de solo lectura. `comprobarVigenciaEnTransaccion` sigue MANDATORY: el escritor existente bloquea al usuario y llama al otro bean mediante proxy; la lectura común reconstruye ahora también el resumen en esa misma unidad transaccional. El constructor REQUIRED se une a la transacción de escritura y no la reemplaza por una lectura independiente. No se llama a capturar/revalidar desde la comprobación final.

No se modifica `PersistenciaRecomendacionesIAServiceImpl`. Cambiar un componente/referencia/fecha/estado de huella afecta la captura y el escritor rechaza CONTEXTO_MODIFICADO. La inferencia continúa fuera de transacción. Las pruebas verifican participación/orden con proxies y mocks, sin certificar exclusión concurrente física PostgreSQL.

La clave de generación existente ya incorpora versión y digest de captura. Por ello no se necesita cambiar su formato ni el escritor. Capturas anteriores V1 no se promueven a V2: se rechazan controladamente al revalidar. Asociaciones/capturas históricas no se reescriben. Una captura nueva puede tener clave distinta y provocar el conflicto existente si hay una asociación pendiente de otro contexto en su categoría. No se elude ese conflicto ni se promete reutilización entre versiones distintas.

## 6. Solicitud Ollama y límite de tamaño

La sobrecarga V2 añade `resumenAmbiental`, separado de `contexto`, `hechosDisponibles` y `accionesCandidatas`. Incluye estado, unidad, período, origen orientativo, comparabilidad, focos y limitaciones. Solo cuando es VIGENTE transmite componentes/perímetros; para huella obsoleta, inválida o no verificable conserva internamente los datos disponibles, sin entregarlos como magnitudes vigentes al modelo.

Para mantenerlo compacto no se transmiten ID de usuario, referencia/fecha/total de huella ni entidades. Esa trazabilidad permanece en la captura. No se inventan rutas de evidencia: las evidencias originales y sus ámbitos son idénticos, especialmente consumo eléctrico HOGAR con valor12.345 intacto en el caso sintético.

El texto adicional del servidor prohíbe deducir dominancia sin foco acreditado y cifras de ahorro/impacto; remite a hábitos si no existe un foco utilizable. La ausencia de foco no es una abstención del modelo ni una habilitación editorial. No cambia el esquema JSON de decisiones, las candidatas permitidas ni la revalidación Java. Con los datos actuales no hay un foco acreditado que habilite acciones: se mantienen las exclusiones y mensajes editoriales de PASO134, sin inventar una exclusión atribuida a Qwen3.

Sin resumen la solicitud V2 anterior se conserva; V1 no recibe este bloque. Se comprueba la solicitud completa UTF-8 antes de transmitir:

| Caso sintético probado | Bytes | Resultado |
|---|---:|---|
| Cuatro categorías, subconjunto Transporte de una acción, sin resumen | 6.997 | Dentro de límite. |
| Mismo subconjunto, con resumen vigente | 7.913 | Dentro de límite; HTTP simulado y selección validada. |
| Catálogo compatible completo, con resumen | 17.169 | Excede16.384: CONTEXTO_INVALIDO controlado antes de HTTP. |

El catálogo completo con resumen **no se transmite** en este escenario. No se trunca JSON, elimina candidatas ni debilita esquema/restricciones para hacerlo caber. La ruta académica restringida conserva su subconjunto y sí cabe. No se inventa una segunda solicitud o reintento para el caso excedido.

## 7. Garantías conservadas y alcance de cambios

No cambian prioridades, basePriorizacion, huellaOrigenId o snapshots; la respuesta reutilizada usa la asociación persistida. El orden técnico134 permanece independiente de impacto. Se conservaron diagnósticos, abstenciones y errores tipados, LEGADO predeterminado, PUBLICABLE rechazado, JWT, historial/completado/conversión y HU37 determinista.

Comparación SHA-256 local antes/después de380 archivos originales de src/docs/pom.xml: 373 idénticos; solo7 existentes modificados, los cinco productivos y dos de prueba autorizados de la lista anterior. Se agregaron5 clases de prueba. Los archivos protegidos, fixtures, treinta variantes y política editorial no se modificaron.

No se conectó PostgreSQL, ejecutó SQL, arrancó servidor, realizó HTTP real o inferencia, ni hubo escrituras Git. Rama R-Carlos conservada. No se implementa el siguiente paso.
