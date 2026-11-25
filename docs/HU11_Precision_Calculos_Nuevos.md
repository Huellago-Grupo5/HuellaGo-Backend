# HU11/HU18: precisión de nuevas huellas

## Alcance y evidencia

Según la verificación de information_schema.columns comunicada por el responsable
del proyecto, las cinco columnas co2 de huellas_carbono son numeric(38,2).
Esta sesión no verifica ni modifica PostgreSQL, entidades, DTO o esquema.

La corrección solo afecta la construcción de nuevos cálculos en calcularHuellaInicial.
recalcularHuella reutiliza ese mismo método. Factores y operaciones intermedias
conservan su precisión original; los cuatro resultados finales se normalizan con
setScale(2, HALF_UP) y el total suma esos componentes ya normalizados.

El mismo total se asigna a la entidad, se devuelve en el DTO y se utiliza para
clasificar el impacto. No se realiza un redondeo independiente del total bruto.
La igualdad total = transporte + energía + alimentación + residuos se garantiza
para las nuevas huellas generadas por este servicio, no retroactivamente.

## Ejemplos técnicos

El factor 0.23825 es provisional, exclusivamente de pruebas; no está científicamente aprobado.
En el fixture con transporte 4, alimentación 2 y residuos 0.7:

| Consumo | Energía sin normalizar | Energía nueva | Total anterior en memoria | Total nuevo |
|---|---|---|---|---|
| 12 kWh/día | 20.013 | 20.01 | 26.713 | 26.71 |
| 20 kWh/día | 33.355 | 33.36 | 40.055 | 40.06 |

Ejemplo de la diferencia de políticas, usando otro factor ficticio solo en el test:
componentes brutos 0.005 + 0.035 + 2 + 0.7 suman 2.740, cuyo redondeo sería 2.74.
Los componentes redondeados suman 0.01 + 0.04 + 2.00 + 0.70 = 2.75; ese es el
total que corresponde guardar y devolver según la política aprobada.

El uso de dos decimales es una regla técnica de almacenamiento y presentación;
no afirma una exactitud ambiental de 0.01 kg ni valida científicamente las fórmulas.

## Compatibilidad y límites

- Históricos: no se actualizan, normalizan ni reconstruyen al consultar.
- Se conservan importes históricos nulos e inconsistencias preexistentes.
- HU14 conserva su orden y tratamiento de ausencias.
- HU16 conserva multiplicadores, redondeo entero y controles técnicos.
- HU19 conserva fechas, reloj America/Lima y periodo semanal.
- HU37 conserva vigencia, priorización por componentes y recomendaciones realizadas.
- Sin umbrales aprobados se conserva NO_DEFINIDO.
- No se alteran límites de almacenamiento numeric(38,2), ni se amplía su rango.

No se agregan factores, umbrales, nuevas metodologías, historias ni sprints.
Continúan pendientes las fuentes aplicables a Perú, alcance personal/hogar,
justificación dimensional de coeficientes heredados y clasificación ambiental aprobada.

## Verificación

Pruebas aisladas de servicio y MockMvc cubren referencias 26.713/40.055, casos
inferiores/iguales/superiores a media centésima, escala 2, igualdad de suma, entidad
vs DTO, clasificación con total canónico, cero, escalas distintas, energía estimada,
recálculo y conservación de históricos. El factor y umbrales de los fixtures no
constituyen referencias científicas. Se conserva la regresión de HU14/HU16/HU19/HU37.

La persistencia es simulada. Queda pendiente la comparación posterior autorizada
de POST, fila PostgreSQL y GET tras flush/clear o nueva petición, sin alterar los
registros de prueba anteriores. No se ejecutaron pruebas PostgreSQL en esta sesión.

Resultados: 120 pruebas específicas aprobadas y regresión segura completa de 619
aprobadas, 0 fallos, 0 errores, 0 omitidas en la selección ejecutada. Incluye 13 casos
nuevos. CuentasPostgresHttpTest y HabitosInicialesPostgresTest se excluyeron explícitamente;
no están certificadas ni contadas como omitidas por Surefire. Maven test: BUILD SUCCESS.
