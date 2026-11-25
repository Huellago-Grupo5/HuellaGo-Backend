# PASO132 — Resultados finales Postman

## Procedencia y resultado

**14/14 peticiones manuales con códigos HTTP esperados.** Las realizó el responsable desde Postman contra127.0.0.1:8081, lanzador académico AplicacionDemoIA127 con controlador habitual y base aislada. El asistente no ejecutó ni repitió ninguna petición. Códigos/cuerpos proceden de confirmaciones expresas del responsable durante131 y, para las dos últimas, del mensaje de132.

Las doce primeras están en target/paso131/evidencias-aceptacion.json. Las dos últimas se consolidan aquí sin modificar ese registro histórico. Los cuerpos no proporcionados íntegramente no se inventan. No se incluyen JWT, Basic Auth, contraseñas, token de sesión o PGPASSFILE.

## Matriz de las14 peticiones

| Nº | Petición (prefijo http://127.0.0.1:8081) | Autenticación | Esperado | Obtenido | Resultado |
|---|---|---|---:|---:|---|
| 1 | POST /__test/demo/ia/token | Basic de usuario sintético | 200 | 200 | Coincide |
| 2 | GET /usuarios/me/recomendaciones/historial | JWT91 | 200 | 200 | Coincide |
| 3 | POST /usuarios/me/recomendaciones/30/convertir-en-reto | JWT91 | 201 | 201 | Coincide |
| 4 | POST /usuarios/me/recomendaciones/30/convertir-en-reto | JWT91 | 409 | 409 | Coincide |
| 5 | PUT /usuarios/me/recomendaciones/30/completar | JWT91 | 200 | 200 | Coincide |
| 6 | PUT /usuarios/me/recomendaciones/30/completar | JWT91 | 409 | 409 | Coincide |
| 7 | GET /usuarios/me/recomendaciones/historial | JWT91 | 200 | 200 | Coincide |
| 8 | GET /usuarios/me/recomendaciones/historial | Sin JWT | 401 | 401 | Coincide |
| 9 | PUT /usuarios/me/recomendaciones/30/completar | Sin JWT | 401 | 401 | Coincide |
| 10 | POST /usuarios/me/recomendaciones/30/convertir-en-reto | Sin JWT | 401 | 401 | Coincide |
| 11 | PUT /usuarios/me/recomendaciones/31/completar | JWT91 | 404 | 404 | Coincide |
| 12 | POST /usuarios/me/recomendaciones/31/convertir-en-reto | JWT91 | 404 | 404 | Coincide |
| 13 | PUT /usuarios/me/recomendaciones/1/completar | JWT91 | 404 | 404 | Coincide |
| 14 | POST /usuarios/me/recomendaciones/1/convertir-en-reto | JWT91 | 404 | 404 | Coincide |

No son14 pruebas JUnit ni14 inferencias. El porcentaje100% se refiere exclusivamente a códigos esperados en este escenario manual, no a calidad estadística del modelo, cobertura de todas las HU o seguridad universal.

## Evidencias funcionales relevantes

- Autenticación200: DEMO_ACADEMICA_LOCAL/publicable=false comunicados. La sesión nueva4 del91 se comprobó físicamente, sin leer el token.
- Historial inicial200:lista vacía, coherente con0 completadas en la referencia inicial.
- Conversión201:usuarioRecomendacionId30, retoId3, participacionId3, estado activo. PostgreSQL confirmó snapshot copiada exactamente, origen/categoría correctos, recompensa0, progreso0 y asociación todavía pendiente en ese punto de control.
- Conversión duplicada409: un solo reto/participación y todas las fechas/contenido intactos en la lectura siguiente.
- Completado200: asociación30 completada; snapshot/fechaGeneracion intactas y sin puntos o completado del reto. Valor HTTP fechaCompletado2026-11-24T13:49:14.7979368; persistido2026-11-24T13:49:14.797937 por redondeo correcto PostgreSQL.
- Completado duplicado409: la primera fecha física se mantuvo exactamente, sin nuevos efectos.
- Historial final200: únicamente30 completada con título/descripcion personalizados, generación2026-11-24T13:23:49.75312 y completado2026-11-24T13:49:14.797937. Se comunicó un resumen de la respuesta, no su JSON literal completo; el historial físico se contrastó mediante SELECT en131.
- Los tres401 devolvieron página **HTML de Tomcat HTTP Status401 Unauthorized**, no un JSON inventado. No se observó mutación asociada. No se cambia el contrato o seguridad para homogeneizar esa presentación durante el cierre.
- Propiedad: recurso31 **existente** del92, consultado con JWT91; PUT/POST404 y fila/fechas originales intactas, cero retos de origen31.
- Inexistencia: id1 confirmado mediante SELECT antes de las peticiones; PUT/POST404; en132 sigue ausente, cero recursos creados. No se reservó ni consumió secuencia para elegirlo.

Respuestas de conflicto comunicadas:

```json
{"mensaje":"La recomendación ya fue convertida en reto"}
```

```json
{"mensaje":"La recomendación ya fue completada"}
```

Respuesta exacta de ambas peticiones ajenas y de ambas inexistentes:

```json
{"mensaje":"Recomendación no encontrada para el usuario"}
```

## Límites de lo demostrado

1. Asociación30 es **fixture de preparación sintética manual130B**, método PREPARACION_SINTETICA_MANUAL_130B/modeloNULL. No fue una inferencia nueva. La generación real Qwen3 corresponde al124; el controlador habitual/reutilización al128, según evidencia confirmada por el responsable.
2. Asociación31 sigue Pendiente: su exclusión del historial **no demuestra por sí sola** el filtrado HTTP de asociaciones ajenas completadas. Sí se ejercitaron propiedad404 en completado/conversión y el código consulta historial por propietario.
3. Solo hay una completada: no se probó por HTTP el orden de varias completadas ni el desempate. El repositorio y los tests existentes aportan evidencia separada, no se la convierte en una prueba manual inexistente.
4. El DTO de historial/completado no contiene motivo ni estados editoriales nuevos; se comprobó motivo íntegro en PostgreSQL. La respuesta de generación es la que tiene los cuatro metadatos de demostración.
5. Esta ronda no prueba prioridades por beneficio, ahorros estimados, comportamiento completo de HU37, interfaz frontend o autorizaciones productivas. No se repitieron las732 pruebas aisladas o15 PostgreSQL.

La evidencia física final, filas y conteos se conservan en verificacion-final-postgresql.md. Las referencias anteriores permanecen sin sobrescribir; no hubo HTTP del asistente, Maven, SQL de escritura o cierre automático del servidor en132.

