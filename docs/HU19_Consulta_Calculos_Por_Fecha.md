# HU19: consulta de cálculos registrados por fecha

## Alcance aprobado y criterios

HU19 pertenece al Sprint 2. La matriz privada remite a IHC FINAL.pdf p. 93:
CA1 seleccionar fecha y consultar impacto; CA2 presentar registros diarios ordenados;
CA3 indicar ausencia de información. Se implementa la consulta fechada del historial
existente, no un sistema nuevo de medición de emisiones diarias.

Una huella semanal calculada en determinada fecha NO equivale a emisiones producidas
ese día. El periodo sigue siendo `semanal`. No se suman cálculos del día, no se divide
entre siete y no se reconstruyen hábitos históricos. La medición diaria real requiere
datos y metodología adicionales; HU19 no queda certificada como medición diaria real.

## Contrato HTTP

Se conserva el DTO HuellaCarbonoRespuestaDTO, sus campos y valores persistidos.
No se agrega ID al JSON, aunque el ID de la entidad se utiliza para desempatar.

### GET /huella-carbono/diario?fecha=2026-11-07

- Fecha opcional; sin ella se consulta hoy en America/Lima.
- Devuelve 200 con el último cálculo de la fecha seleccionada.
- Orden: fechaCalculo DESC, id DESC. Nunca suma estimaciones sucesivas.
- Sin registros: 404
  `{"mensaje":"No hay cálculos de huella de carbono registrados para la fecha seleccionada."}`.

### GET /huella-carbono/historial?fecha=2026-11-07

- Fecha opcional; sin ella se conserva exactamente la consulta completa anterior.
- Con fecha devuelve 200 con todos los cálculos registrados ese día, ordenados por
  fechaCalculo DESC, id DESC. Sin registros: 200 `[]`.

Ambas operaciones obtienen el propietario del JWT; no admiten seleccionar al propietario
mediante usuarioId. Un query param adicional usuarioId no modifica la identidad consultada.
No existe una operación nueva para acceder a datos ajenos.

## Validación y errores

El formato admitido es AAAA-MM-DD, con fecha de calendario válida y año 0001–9999.
Fecha vacía, con espacios, formato alternativo, fecha imposible o año expandido:
400 `{"mensaje":"La fecha debe ser válida y tener formato AAAA-MM-DD."}`.
Año cero o fecha fuera del rango admitido en el servicio:
400 `{"mensaje":"La fecha está fuera del rango admitido."}`.
La fecha proporcionada no se normaliza silenciosamente ni se interpreta como un instante UTC.
Los errores de consulta conservan 503 mediante HuellaCarbonoErrorHandler, sin detalles SQL.
Sin JWT activo: 401 mediante la seguridad existente.

## Consultas y zona horaria

Intervalo compartido: fechaCalculo >= inicio del día AND fechaCalculo < inicio del
día siguiente. La medianoche del día siguiente queda excluida. Se comprueba el rango
antes de construir el intervalo y se controlan errores temporales de construcción.
Para 9999-12-31 el límite exclusivo es 10000-01-01, sin desbordar LocalDateTime;
su ejecución en PostgreSQL queda pendiente de verificación real.

ConsultaHuellaConfiguration proporciona el bean huellaConsultaClock con America/Lima.
El servicio lo recibe por inyección y determina hoy explícitamente en esa zona.
No se cambia la zona global de la JVM ni la configuración de IntelliJ/application.properties.
El reloj no se utiliza para modificar el cálculo de HU11 ni las fechas de escritura existentes.

Los registros históricos usan LocalDateTime sin zona. Se filtran sus valores locales
tal como están almacenados: no se reinterpretan como UTC ni se corrigen. Su interpretación
como hora de Lima requiere validación histórica/metodológica. El reloj explícito solo fija
qué significa hoy para esta consulta; no acredita la zona de los datos históricos.

## Compatibilidad y pruebas

Se mantienen las firmas de servicio de un argumento y se añaden sobrecargas con LocalDate.
Los nuevos métodos de repositorio expresan >= y < y el orden mediante consultas derivadas.
El método anterior Between se conserva para compatibilidad, pero HU19 deja de utilizarlo.
No se modifican DTO, entidades, esquema, fórmulas, HU16 ni la corrección de HU37.

Pruebas aisladas:

- Fechas explícitas y omitidas, reloj fijo UTC que corresponde al día anterior en Lima.
- Mes/año, bisiestos, fechas inválidas, año cero, extremos de LocalDate y rango admitido.
- Varios cálculos, empate por fecha y desempate por ID; inclusión/exclusión de medianoches.
- Importes almacenados, periodo semanal, historial completo y filtrado vacío.
- HTTP 400/401/404/503, identidad JWT, aislamiento y ausencia de escrituras.
- PartTree de Spring Data comprueba propietario, operadores, orden y límite Top sin datasource.

Los repositorios se simulan en servicio/MVC. PartTree comprueba la interpretación de los
nombres de métodos, no la ejecución SQL. Persistencia, precisión temporal y comportamiento
real de PostgreSQL deben certificarse posteriormente con autorización.
Los fixtures MVC anteriores de recálculo y equivalencias importan únicamente la nueva
configuración del reloj; se conservan sus casos y assertions.

Resultado de la sesión: 52 pruebas específicas aprobadas y regresión segura de 606
aprobadas, 0 fallos, 0 errores y 0 omitidas en la selección ejecutada. Incluye 38 casos
nuevos. CuentasPostgresHttpTest y HabitosInicialesPostgresTest quedaron excluidas
explícitamente, no ejecutadas. Maven test: BUILD SUCCESS. No sumar ambas ejecuciones
como pruebas distintas ni considerar los repositorios simulados certificación PostgreSQL.

## Comprobaciones manuales posteriores: no ejecutadas

Con una aplicación previamente autorizada y verificada contra huellagodb_hu29_test:

1. Usuario 68: GET /huella-carbono/diario?fecha=2026-11-07. Esperar el último cálculo
   de ese día (ID 6 según la evidencia manual previa), periodo semanal y valores persistidos.
2. GET /huella-carbono/historial?fecha=2026-11-07. Esperar los dos cálculos previos,
   orden 6 luego 5 si no se registraron otros; no un total agregado.
3. GET /huella-carbono/historial sin fecha: conservar todo el historial.
4. Día conocido sin registros: diario 404 e historial 200 [].
5. fecha=2026-02-29, fecha=07-11-2026 y fecha vacía: 400 con mensaje controlado.
6. Sin JWT: 401. Otro JWT: únicamente sus propios cálculos.

Tras medianoche en Lima, /diario sin fecha consulta el nuevo día; para revisar el
7 de noviembre debe enviarse fecha=2026-11-07. No hay fallback al último día con datos.
No se ejecutaron conexiones, migraciones ni operaciones sobre registros existentes.
