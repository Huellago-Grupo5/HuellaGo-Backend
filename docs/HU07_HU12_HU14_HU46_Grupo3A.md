# Grupo 3A: objetivos, dashboard, desglose y consulta de hábitos

## Alcance y criterios

Fuentes: matriz privada de las 56 HU y documento original IHC FINAL.pdf,
páginas físicas 89 (HU07), 91–92 (HU12/HU14) y 102 (HU46).
Se conserva la planificación: HU07 no asignada, HU12/HU14 Sprint 1 y HU46 Sprint 2.

| HU | CA1: responsabilidad backend | CA2: responsabilidad backend | CA3: responsabilidad backend |
|---|---|---|---|
| HU07 | Catálogo y selección de uno o más objetivos | Sustitución transaccional de selección propia | Selección vacía rechazada con 400 |
| HU12 | Resumen ambiental propio con categorías normalizadas | Datos existentes para los accesos; navegación corresponde al frontend | 404/503 controlados; presentación del mensaje corresponde al frontend |
| HU14 | Cuatro componentes ambientales | Datos ordenados para gráficos; gráficos corresponden al frontend | Ausencias explícitas, distintas de cero |
| HU46 | Consulta de IDs propios y actualización existente | Recálculo existente disponible después del PUT; no se recalcula implícitamente | Validaciones existentes conservadas |

## HU07: integridad de la selección

PUT `/usuarios/me/objetivos`, body `{"objetivoIds":[1,2]}`, devuelve 200 con la
lista de objetos `{id,nombre,descripcion}`. Los IDs deben existir en el catálogo.
GET `/objetivos-ambientales` y GET `/usuarios/me/objetivos` no cambian.

El usuario se obtiene del correo autenticado. Se valida antes de borrar o insertar.
Una lista vacía, nula o con IDs nulos/no positivos produce 400; objetivos inexistentes,
404. Los IDs repetidos se deduplican, conservando el contrato previo.

La transacción usa explícitamente READ_COMMITTED. Antes de leer asociaciones,
se reutiliza `UsuarioRepository.bloquearParaRecomendaciones`: bloqueo FOR UPDATE
de la fila del usuario, mantenido hasta commit/rollback. Una segunda solicitud del
mismo usuario espera y lee la selección confirmada por la anterior. Se conservan
las asociaciones compartidas y se eliminan solamente las obsoletas.
No se añade un mutex local a producción ni se cambia el esquema.
Las escrituras externas que no respetan este bloqueo quedan fuera de esta garantía.

## HU12: normalización del resumen

GET `/dashboard` conserva todos sus campos. Una consulta JPQL agrupa y cuenta
hábitos del usuario por nombre de categoría. Se fusionan las variantes usando
`ValidacionHabitosIniciales.normalizar`, ya utilizada para hábitos y configuración:
NFD, eliminación de marcas diacríticas, strip y minúsculas con Locale.ROOT.

El mapa conserva Transporte, Energía, Alimentación y Residuos, con cero cuando
no hay registros. `cantidadHabitos` cuenta filas, no categorías completas ni acciones
sostenibles verificadas. Las filas históricas sin categoría siguen contando en el
total, sin atribuirlas a una categoría inventada. Otras categorías permanecen en el
cálculo del total y de la categoría predominante, sin ampliar el mapa contractual.
La categoría predominante se determina después de fusionar variantes; los empates
se resuelven por nombre ascendente mediante el orden de String de Java.
Sin hábitos: total cero y categoría predominante null. Sin huella: valor null.

Un usuario inexistente produce 404 `{"mensaje":"Usuario no encontrado"}`.
Un fallo de acceso a datos produce 503
`{"mensaje":"No se pudo cargar el dashboard en este momento"}` sin detalles SQL.

## HU14: contrato aditivo para registros históricos incompletos

GET `/huella-carbono/desglose/{usuarioId}` mantiene usuarioId, co2Total y actividades.
Añade `completa` a cada actividad y `completo`/`categoriasIncompletas` al resumen:

```json
{
  "usuarioId": 9001,
  "co2Total": 3,
  "actividades": [
    {"categoria":"Transporte","co2":3,"completa":true},
    {"categoria":"Residuos","co2":0,"completa":true},
    {"categoria":"Energía","co2":null,"completa":false},
    {"categoria":"Alimentación","co2":null,"completa":false}
  ],
  "completo": false,
  "categoriasIncompletas": ["Energía","Alimentación"]
}
```

Orden: CO2 descendente, null al final; empates preservan el orden canónico
Transporte, Energía, Alimentación, Residuos. Un cero real es completo. Nunca se
reemplaza null por cero, ni se calcula un total nuevo para reparar datos históricos.
`completo` requiere cuatro componentes y total presentes; no certifica la validez
científica del cálculo ni revalida su suma. Un total null permanece null.

Con una huella persistida, incluso con todos los componentes null: 200 e incompleto.
Sin registro de huella: 404 y mensaje existente. Usuario ajeno: 403. Sin JWT: 401.
El frontend debe omitir o marcar visualmente los valores ausentes al graficar.

## HU46: consulta propia y edición

Nuevo GET `/habitos`, autenticado, sin body ni usuarioId requerido. La identidad
proviene exclusivamente del principal autenticado; un query param usuarioId ajeno
no cambia el propietario consultado. Devuelve 200 con array ordenado por ID ascendente:

```json
[
  {
    "id":701,"usuarioId":9001,"categoriaId":1,"categoriaNombre":"Transporte",
    "nombre":"diasSemana","valor":5,"unidad":"dias/semana",
    "fechaRegistro":"2026-11-07T10:00:00",
    "fechaActualizacion":"2026-11-07T10:00:00"
  }
]
```

Sin registros: 200 `[]`. Se conserva el nombre de categoría almacenado.
Una categoría histórica ausente se representa con categoriaId/categoriaNombre null.
PUT `/habitos/{id}` conserva body `{nombre,valor,unidad}`, validaciones y eventos;
su respuesta incorpora de manera aditiva categoriaNombre. Se conserva el constructor
Java anterior de ocho argumentos del DTO. No cambia el almacenamiento de unidad.
No se admite cambiar nombre o categoría con ese PUT.

Errores: 401 sin autenticación, 403 al editar un hábito ajeno, 404 ID inexistente,
400 campos inválidos, 503 acceso a persistencia fallido con mensaje controlado.
POST `/huella-carbono/recalcular` permanece independiente y sin cambios.

## Verificación automatizada y límites

Pruebas JUnit/Mockito y MockMvc aisladas, sin datasource ni aplicación real.
`ObjetivosTransaccionSimuladaTest` ejecuta el proxy transaccional real con un gestor
y recurso simulados: exclusión mutua, sustitución completa, rollback tras un fallo,
conservación de asociaciones y aislamiento. No certifica locks ni rollback PostgreSQL.

La regresión excluye explícitamente `CuentasPostgresHttpTest` y
`HabitosInicialesPostgresTest`, además de dejar sus propiedades opt-in en false.
Resultado de la regresión segura: 536 pruebas aprobadas, 0 fallos, 0 errores y
0 omitidas dentro de la selección; las dos clases PostgreSQL quedaron excluidas,
no aprobadas ni contadas como omitidas por Surefire. Incluye 26 casos nuevos.
La ejecución específica anterior aprobó 40 pruebas; los cuatro casos añadidos
después quedaron incluidos en las 536. Maven test y compile: BUILD SUCCESS.
No se realizaron conexiones, migraciones, cambios JPA ni pruebas manuales reales.

## Secuencia manual pendiente: NO ejecutada

Solo con autorización posterior: configurar externamente el datasource explícito a
`jdbc:postgresql://localhost:5432/huellagodb_hu29_test`, sql.init.mode=never,
ddl-auto=validate y huellago.cierre-colectivo.enabled=false. No editar application.properties.
Verificar la conexión efectiva antes de iniciar la aplicación y usar dos usuarios nuevos
propios de prueba; no escribir sobre los usuarios 64 ni 65.

1. En esa conexión explícita ejecutar SELECT current_database(); continuar solo si
   devuelve exactamente huellagodb_hu29_test.
2. GET /objetivos-ambientales; elegir IDs reales. PUT /usuarios/me/objetivos con dos
   listas concurrentes distintas: el resultado debe ser una lista íntegra, no la unión.
   Verificar GET /usuarios/me/objetivos y asociaciones con SELECT. Probar vacío 400,
   repetidos idempotentes y un ID realmente inexistente 404 sin cambiar la selección.
3. GET /dashboard después del registro de hábitos: contrastar total y categorías con
   SELECT; revisar registros históricos con/sin tilde si existen, sin modificarlos.
4. GET /huella-carbono/desglose/{idPropio}: revisar cuatro categorías, orden y metadatos.
   Si no existen huellas históricas con null, mantener ese escenario pendiente; no
   introducir ni modificar datos históricos para simularlo durante esta revisión.
5. GET /habitos: tomar un ID real. PUT /habitos/{id} con
   `{"nombre":"diasSemana","valor":6,"unidad":"dias/semana"}` solo si corresponde
   al campo obtenido. Consultar nuevamente y ejecutar POST /huella-carbono/recalcular.
6. Con el segundo JWT, comprobar que GET solo lista sus filas y editar el ID del primero
   produce 403. Sin JWT, los endpoints protegidos deben producir 401.

Consultas de lectura preparadas, no ejecutadas. Sustituir :idPropio únicamente por el
ID del usuario nuevo de prueba autenticado (parámetro ilustrativo; pgAdmin requiere valor):

```sql
SELECT current_database();
SELECT id, nombre FROM objetivos_ambientales ORDER BY id;
SELECT id, usuario_id, objetivo_id FROM usuarios_objetivos_ambientales
WHERE usuario_id = :idPropio ORDER BY objetivo_id;
SELECT h.id, h.usuario_id, c.nombre AS categoria, h.nombre, h.valor, h.unidad
FROM habitos h LEFT JOIN categorias_habito c ON c.id = h.categoria_id
WHERE h.usuario_id = :idPropio ORDER BY h.id;
SELECT c.nombre, COUNT(*) FROM habitos h
LEFT JOIN categorias_habito c ON c.id = h.categoria_id
WHERE h.usuario_id = :idPropio GROUP BY c.nombre ORDER BY c.nombre;
SELECT id, co2_total, co2_transporte, co2_energia, co2_alimentacion, co2_residuos
FROM huellas_carbono WHERE usuario_id = :idPropio
ORDER BY fecha_calculo DESC, id DESC;
```

Pendiente real: validación de JPQL/SQL y proyección contra PostgreSQL, serialización
entre conexiones independientes, rollback persistente y HTTP con JWT/sesiones reales.
No se cambia el login ni los factores, umbrales, recomendaciones o cálculos ambientales.
