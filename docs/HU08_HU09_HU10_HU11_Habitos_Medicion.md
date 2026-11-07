# HU08–HU11: hábitos y medición inicial

## Alcance y fuentes

Implementación de Arquitectura Web basada en la lectura íntegra de
`HuellaGo_IHC_56_HU_Criterios_Auditoria.md`. La matriz atribuye HU08 a IHC FINAL.pdf
p. 89 y HU09–HU11 a p. 90. Se localizó además el original en
C:\Users\GaToFachero\Downloads\IHC FINAL.pdf y se contrastaron íntegramente esas dos
páginas con PDFBox. Sus doce criterios coinciden con la matriz. La búsqueda del texto
completo no identificó factores de emisión, kWh ni umbrales numéricos aplicables a
estas HU. No se modificó la matriz. Las pantallas y redirecciones son frontend.

No se modificaron cuentas, login, recuperación, perfil ni Spring Security.
El login conserva exclusivamente token, id y correo.

## Cobertura de criterios

| HU | CA1 | CA2 | CA3 |
|---|---|---|---|
| HU08 | Registro transaccional de los tres campos | Medio y frecuencia asociados al usuario autorizado | Campos obligatorios, opciones, km positivos y días enteros 1–7 |
| HU09 | Modelo anterior conservado y consumo eléctrico diario opcional persistido | 201 permite continuar; navegación corresponde al frontend | Validaciones de vivienda, personas, fuente y consumo |
| HU10 | Devuelve completa solamente si las cuatro categorías son válidas | El cálculo existente produce resultado si los datos están completos | Devuelve categorías pendientes; formulario propio inválido produce 400 |
| HU11 | Valida y suma las cuatro categorías; conserva persistencia | Clasificador configurable implementado; sin umbrales aprobados devuelve NO_DEFINIDO | Datos incompletos, duplicados o incompatibles producen 400 |

HU11 CA2 queda condicionado a definir y aprobar umbrales y su referencia.
No se certifica validez científica de los coeficientes heredados ni se inventan umbrales.

## Arquitectura y persistencia

Se mantiene Controller → Service → ServiceImpl → Repository. Bean Validation
actúa en los POST de hábitos y los servicios conservan validaciones explícitas.
`ValidacionHabitosIniciales` comparte reglas entre actualización, comprobación de
configuración y cálculo. Un campo obligatorio debe existir exactamente una vez,
con valor y unidad compatibles; contar filas o categorías no prueba completitud.

Los tres POST de hábitos y PUT /habitos/{id} usan el bloqueo de usuario existente
`UsuarioRepository.bloquearParaRecomendaciones` (SELECT ... FOR UPDATE), dentro
de una transacción. La comprobación de duplicados ocurre después del bloqueo.
El cálculo usa el mismo bloqueo antes de leer hábitos. Esto serializa las
operaciones del mismo usuario entre instancias de la aplicación, bajo el aislamiento
READ COMMITTED habitual de PostgreSQL. Usuarios diferentes bloquean filas diferentes.
No se agregó un mutex local como protección de producción.

El registro de alimentación y residuos sigue siendo una sola transacción. La respuesta
consulta el estado persistido de las cuatro categorías antes de confirmar la transacción.
Un 201 con configuracionInicialCompleta=false guarda esos datos válidos, pero no finaliza
la configuración; el cliente debe solicitar las categorías indicadas. El cálculo las
rechaza hasta completarlas. No se agregó un endpoint ni una bandera en la entidad Usuario.

No se requiere migración: el consumo opcional se representa mediante una fila adicional
de la tabla EAV `habitos`: nombre=consumoKwhDiario, unidad=kWh/dia, valor no negativo.
Las entidades, columnas y tablas permanecen iguales. Las escrituras SQL externas no
están protegidas por la disciplina de bloqueo del servicio. No se repararon datos antiguos.

## Contratos HTTP

Todas las operaciones requieren JWT activo. Sin autenticación: 401. Usuario ajeno: 403.
Formulario inválido: 400. Categoría ya registrada: 409. Usuario/categoría inexistente: 404.
Errores de persistencia en hábitos: 503 sin SQL ni detalles internos.

POST /habitos/transporte — 201; respuesta y body anteriores conservados:

```json
{"usuarioId":9001,"medio":"Bus","kmSemana":120,"diasSemana":5}
```

Medios: Bicicleta, Caminando, Bus, Auto, Moto. kmSemana es la distancia semanal total,
estrictamente positiva; no se vuelve a multiplicar por días. diasSemana debe estar
entre 1 y 7. Los kilómetros cero siguen rechazados por compatibilidad con PUT y cálculo.

POST /habitos/energia — 201. El body antiguo sigue admitido:

```json
{"usuarioId":9001,"vivienda":"Casa","personas":3,"fuente":"Electricidad"}
```

Vivienda: Casa/Departamento. Personas: entero positivo. Fuente: Electricidad/Gas/Solar.
El campo adicional consumoKwhDiario es opcional y se devuelve en la respuesta, null
si no se informó. Representa el consumo eléctrico diario del hogar completo:

```json
{"usuarioId":9001,"vivienda":"Casa","personas":3,"fuente":"Electricidad","consumoKwhDiario":4.5}
```

No se transforma gas ni generación solar a kWh sin un contrato específico. Si se informa
consumoKwhDiario con Gas/Solar o un valor negativo, se devuelve 400. Cero consumo es válido.
Para modificar una fila de consumo ya existente se usa PUT /habitos/{id}:

```json
{"nombre":"consumoKwhDiario","valor":5,"unidad":"kWh/dia"}
```

No se permite cambiar a Gas/Solar una configuración que conserva consumo eléctrico.
El POST repetido continúa devolviendo 409, incluso si se intenta añadir consumo a una
categoría antigua. La incorporación posterior de ese campo a categorías ya registradas
requiere acordar una ampliación de actualización; no se agregó un endpoint por anticipado.

POST /habitos/alimentacion-residuos — 201:

```json
{"usuarioId":9001,"tipo":"Mixta","plasticos":"Medio","reciclas":true}
```

Tipo: Vegana/Vegetariana/Mixta. Plásticos: Bajo/Medio/Alto. reciclas es obligatorio,
incluido false. La respuesta conserva sus seis campos originales y añade:

```json
{"configuracionInicialCompleta":false,"categoriasPendientes":["Transporte","Energía"]}
```

Si todos los campos de las cuatro categorías son válidos, devuelve true y [].
Las categorías con datos inválidos o duplicados también se consideran pendientes.

POST /huella-carbono/calcular — sin body; usa exclusivamente el usuario del JWT.
Respuesta del ejemplo antiguo completo:

```json
{
  "usuarioId":9001,
  "co2Total":10.5,
  "co2Transporte":6,
  "co2Energia":1.8,
  "co2Alimentacion":2,
  "co2Residuos":0.7,
  "fechaCalculo":"fecha generada por el servidor",
  "unidad":"kg CO2e",
  "periodo":"semanal",
  "nivelImpacto":"NO_DEFINIDO",
  "referenciaImpacto":"Sin umbrales aprobados",
  "metodologia":"Modelo orientativo HuellaGo; coeficientes heredados sin validacion cientifica"
}
```

Los campos nuevos también aparecen en consultas de la misma clase DTO. Se conservan
los nombres, tipos y constructores usados por dashboard, historial, recálculo y reportes.

## Metodología y limitaciones

Los valores siguientes ya existían. Se documentan como coeficientes del prototipo,
sin atribuirles respaldo científico que no figura en el repositorio:

- Transporte = kmSemana × factor: Bicicleta/Caminando 0, Bus 0.05, Auto 0.12, Moto 0.08.
- Energía estimada = vivienda × fuente × (1 + (personas − 1) × 0.25).
  Vivienda: Casa 1.2, Departamento 0.8; fuente: Electricidad 1, Gas 0.85, Solar 0.25.
- Alimentación = tipo × 2; Vegana 0.3, Vegetariana 0.5, Mixta 1.
- Residuos = plásticos × reciclaje × 2; plásticos Bajo 0.2, Medio 0.5, Alto 0.9;
  reciclaje 0.7 si recicla, 1 si no.
- Total = suma de las cuatro categorías. El contrato heredado lo expresa en kg CO2e
  semanales, pero los coeficientes requieren calibración y revisión dimensional.

Con consumo eléctrico explícito, energía = consumoKwhDiario × 7 × factor en kg CO2e/kWh.
No se multiplica por personas ni vivienda, porque el dato representa al hogar completo.
El factor y su referencia deben configurarse externamente; sin ellos se devuelve 503
antes de guardar la huella, en vez de ignorar el consumo o inventar una conversión.
El resto de categorías sigue siendo estimado. Los totales nuevos pueden diferir del
modelo anterior; una comparación entre metodologías no implica una reducción real.

## Configuración externa, sin editar application.properties

Propiedades opcionales para consumo eléctrico medido:

- huellago.medicion.electricidad.factor-kg-co2e-kwh
- huellago.medicion.electricidad.referencia

Debe aprobarse un factor de alcance geográfico, periodo y tecnología apropiados,
con fuente verificable. No se proporciona un valor por defecto ni credenciales.

Propiedades opcionales de clasificación, expresadas en kg CO2e semanales:

- huellago.medicion.impacto.limite-bajo
- huellago.medicion.impacto.limite-medio
- huellago.medicion.impacto.referencia

BAJO: total <= límite bajo. MEDIO: límite bajo < total <= límite medio. ALTO: total >
límite medio. Debe cumplirse 0 <= bajo < medio y referencia no vacía. Si se omiten
las tres propiedades: NO_DEFINIDO. Configuración parcial o inválida: 503, sin persistir
el nuevo cálculo. Los valores numéricos de los tests son ficticios, solo prueban ramas.
La clasificación y referencia se calculan con la política actual; no se congelan en
las filas históricas. Una política científicamente aprobada sigue pendiente.

## Verificación

Tests nuevos: HabitosInicialesCorreccionesTest, PoliticaMedicionInicialTest,
HabitosInicialesControllerTest y HabitosInicialesPostgresTest. Se mantienen los
tests anteriores y se actualizan fixtures para simular explícitamente el bloqueo.
Incluyen límites, obligatorios, duplicados, aislamiento, completitud, consumo explícito,
ausencia de factor, clasificación configurable y concurrencia con bloqueo simulado.

Las pruebas de PostgreSQL nuevas son opt-in mediante -Dhuellago.habitos.realDb=true,
con BasePruebasGuard verificando destino efectivo y SELECT current_database() antes
del datasource. Usan solo usuarios con correo hu08-11-<UUID>@example.invalid,
@Transactional y @Rollback; no usan ni modifican el usuario 64. No inician servidor HTTP.
Requieren categorías existentes. Sus credenciales deben suministrarse externamente.
El consumo de valores de secuencias no se revierte por PostgreSQL.

No ejecutar el opt-in antes de comprobar URL, perfiles, permisos y las condiciones
de seguridad exigidas por el proyecto. La suite ordinaria usa -Dhuellago.habitos.realDb=false
y excluye CuentasPostgresHttpTest. No se ejecutaron conexiones PostgreSQL en esta sesión.
Quedan pendientes persistencia/rollback contra PostgreSQL real y concurrencia entre
transacciones reales; la prueba concurrente de Mockito no los certifica.

Resultado de ejecución final: Maven BUILD SUCCESS; 504 casos reportados, 502 ejecutados
y aprobados, 0 fallidos, 0 errores y 2 omitidos (HabitosInicialesPostgresTest).
CuentasPostgresHttpTest se excluyó explícitamente y no forma parte de esos 504 casos.
La ejecución específica previa pasó 60 casos; la regresión final también incluye los
12 casos HTTP nuevos de HabitosInicialesControllerTest. No sumar estas ejecuciones
como casos distintos. Un primer intento detectó cuatro errores de contexto porque
faltaba importar PoliticaMedicionInicial en el slice MVC de recálculo; se corrigió
el fixture y se verificó de nuevo. No se redujeron assertions para conseguir el éxito.

## Postman

Usar un usuario de prueba nuevo propio y su JWT activo. Reemplazar usuarioId por su id,
sin reutilizar el 64 ni alterar datos manuales. Con el servidor previamente verificado
en huellagodb_hu29_test, registrar transporte → energía → alimentación/residuos → calcular.
Esperar 201/201/201/200, true/[] en la tercera operación y total 10.5 en la última para
los ejemplos anteriores. Probar días 0/8, km negativos, campos omitidos, opciones
desconocidas: 400. Repetir POST de categoría: 409. En dos solicitudes simultáneas
del mismo transporte: una 201 y otra 409, solamente tres filas de esa categoría.
Sin JWT: 401; usuarioId ajeno con body válido: 403. Registrar primero solo alimentación:
201 con false y Transporte/Energía pendientes; calcular: 400. Con consumo real y sin
factor aprobado: registro 201, cálculo 503 sin nueva huella. No esperar BAJO/MEDIO/ALTO
sin aprobar y suministrar la política externa.
