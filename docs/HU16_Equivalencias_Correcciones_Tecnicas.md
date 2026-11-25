# HU16: correcciones técnicas de equivalencias

## Alcance

GET `/huella-carbono/equivalencias` conserva autenticación, identidad del JWT y DTO:
`usuarioId`, `co2Total`, `arboles`, `bicicletaKm`, `reciclajeKg`.
No se modifican fórmulas de huella, entidades, esquema, HU11, HU19 ni HU37.

Los multiplicadores heredados 10, 50 y 7 se conservan. Las equivalencias son
orientativas y carecen todavía de respaldo metodológico suficiente: no representan
conversiones científicamente certificadas. Faltan aprobar las fuentes, el significado
de cada equivalencia, sus unidades, periodo y alcance. Esta corrección no cierra HU16.

## Comportamiento

- Total cero: las tres equivalencias son cero.
- Se elimina el mínimo artificial de 1 también para cantidades positivas pequeñas.
- Se conserva redondeo entero HALF_UP después de multiplicar.
- Total histórico null o negativo: 404 con
  `{"mensaje":"No hay una huella de carbono válida para mostrar equivalencias"}`.
- Resultado redondeado fuera del rango Long: 404 con
  `{"mensaje":"La huella de carbono excede el rango admitido para mostrar equivalencias"}`.
- No hay huella: se conserva 404 `{"mensaje":"Huella de carbono no encontrada"}`.
- Fallos de persistencia: se conserva el manejador 503; no se convierten en 404.

Se sigue la convención existente de comparación de huellas: 404 cuando no hay una
huella utilizable para la operación. No se agregan códigos ni campos al contrato.
No se reparan registros históricos ni se sustituyen ausencias por cero.

La conversión usa longValueExact para detectar desbordamiento después del redondeo.
El máximo representable es Long.MAX_VALUE; un producto máximo + 0.49 todavía redondea
al máximo, mientras máximo + 0.5 ya se rechaza. Los errores no exponen la excepción interna.

## Verificación

Pruebas de servicio y MockMvc con repositorios simulados: cero, positivos pequeños,
HALF_UP, null, negativos, ausencia de huella, límites Long, desbordamiento, identidad
JWT, aislamiento, 401 y error de persistencia 503. No certifican PostgreSQL real.
La regresión excluye CuentasPostgresHttpTest y HabitosInicialesPostgresTest.
