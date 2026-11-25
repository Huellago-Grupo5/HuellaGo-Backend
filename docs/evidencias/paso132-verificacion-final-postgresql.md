# PASO132 — Verificación final PostgreSQL

## Resultado y alcance

**SATISFACTORIA:19 condiciones verificadas, todas true.** Consulta física real a 2026-11-24T14:10:39.096146-05:00, posterior a las14 peticiones manuales comunicadas. No se ejecutó HTTP ni una inferencia en este paso. No se detuvo el servidor.

Destino comprobado antes de consultar tablas:

```json
{
  "rol": "huellago_ia119_test",
  "base": "huellagodb_ia_integracion_test",
  "fecha": "2026-11-24T14:10:39.096146-05:00",
  "puerto": 5432,
  "seccion": "destino",
  "direccion": "::1",
  "rol_sesion": "huellago_ia119_test",
  "solo_lectura": "on",
  "defecto_solo_lectura": "on"
}
```

Host de conexión localhost, resolución loopback IPv6 ::1, puerto5432. Base única huellagodb_ia_integracion_test; current_user/session_user huellago_ia119_test, sin privilegios administrativos directos/heredados. psql17 con -X -w ON_ERROR_STOP=1; PGOPTIONS default_transaction_read_only=on, statement_timeout15s y lock_timeout3s. SQL por pipe con $OutputEncoding UTF-8 y PGCLIENTENCODING=UTF8. No se leyó, imprimió, copió ni modificó el archivo PGPASSFILE; no se conserva su ruta.

Transacción ejecutada: **BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY → SELECT → ROLLBACK**. Retorno psql0, cierre ROLLBACK observado. Ningún INSERT/UPDATE/DELETE/DDL, avance de secuencia, COMMIT o reparación de registros.

## Referencias utilizadas

- Asociación29 y canónica29: JSONB originales de target/paso130A/propuesta-fixtures.sql, cotejados localmente por igualdad completa con las copias del manifiesto130B; las mismas referencias se emplearon en las igualdades SQL.
- Asociación31/hábitos originales: manifiesto130B.
- Asociación30: fila completa conservada tras el primer completado del131, incluida su fecha física; no se reconstruyó desde el reloj ni desde una nueva generación.
- Reto3/participación3: filas completas de la primera conversión real131, conservadas en evidencias-aceptacion.json.

Las evidencias129/130/131 permanecen históricas, sin sobrescritura. El fixture30 es preparación sintética manual, no una inferencia nueva de Qwen3.

## Condiciones evaluadas

| Condición SQL | Resultado |
|---|---|
| conteos_esperados | true |
| canonica29_jsonb130a | true |
| reto3_jsonb_original | true |
| asociacion1_no_existe | true |
| participacion3_activa | true |
| asociacion29_jsonb130a | true |
| usuario91_puntos_nivel | true |
| sin_retos_origen1_29_31 | true |
| habitos_originales_jsonb | true |
| reto3_contenido_y_estado | true |
| asociacion31_jsonb_original | true |
| unicidad_reto_participacion | true |
| asociacion31_pendiente_ajena | true |
| usuario92_sin_sesion_habitos | true |
| asociacion29_pendiente_propia | true |
| asociacion30_jsonb_completada | true |
| participacion3_jsonb_original | true |
| usuario92_puntos_nivel_sin_clave | true |
| asociacion30_estado_fecha_y_metodo | true |

Se contrastó la fila completa, no solo estado o título. La asociación29 sigue del91/Pendiente/completadoNULL y sin reto; canónica29 conserva identidad/categoría/contenido. La31 sigue del92/Pendiente/completadoNULL y sin reto. La1 sigue ausente y no existe un reto con origen1,29 o31: los404 no crearon recursos.

## Filas físicas observadas

### Asociaciones29,30 y31

```json
[
  {
    "id": 29,
    "estado": "Pendiente",
    "enfoque": null,
    "usuario_id": 91,
    "fecha_completado": null,
    "fecha_generacion": "2026-11-10T00:26:11.320352",
    "huella_origen_id": null,
    "base_priorizacion": "HABITOS_REGISTRADOS",
    "metodo_generacion": "OLLAMA_SELECCION_PRESENTACION_CONTROLADA",
    "modelo_generacion": "qwen3:4b-instruct",
    "motivo_instantanea": "Registraste Auto, 100 km/semana y 5 días/semana. No conocemos tus próximas gestiones.",
    "titulo_instantanea": "Comprueba cómo organizar tus gestiones",
    "recomendacion_ia_id": 29,
    "referencia_contexto": "IA_GEN_V1:493ea81b692b6b82e94d65977523722b267246ee76ec1106c3b7b0a23f72788b",
    "version_presentacion": "1",
    "prioridad_instantanea": "MEDIA",
    "descripcion_instantanea": "Anota tus próximas gestiones y comprueba qué necesidades debe cubrir cada salida antes de organizarla."
  },
  {
    "id": 30,
    "estado": "completado",
    "enfoque": null,
    "usuario_id": 91,
    "fecha_completado": "2026-11-24T13:49:14.797937",
    "fecha_generacion": "2026-11-24T13:23:49.75312",
    "huella_origen_id": null,
    "base_priorizacion": "HABITOS_REGISTRADOS",
    "metodo_generacion": "PREPARACION_SINTETICA_MANUAL_130B",
    "modelo_generacion": null,
    "motivo_instantanea": "Registraste Auto, 100 km/semana y 5 días/semana. No conocemos tus próximas gestiones.",
    "titulo_instantanea": "Comprueba cómo organizar tus gestiones",
    "recomendacion_ia_id": 29,
    "referencia_contexto": "FIXTURE_130B_MANUAL_PROPIO_V1",
    "version_presentacion": "1",
    "prioridad_instantanea": "MEDIA",
    "descripcion_instantanea": "Anota tus próximas gestiones y comprueba qué necesidades debe cubrir cada salida antes de organizarla."
  },
  {
    "id": 31,
    "estado": "Pendiente",
    "enfoque": null,
    "usuario_id": 92,
    "fecha_completado": null,
    "fecha_generacion": "2026-11-24T13:23:49.75312",
    "huella_origen_id": null,
    "base_priorizacion": "CONTROL_PROPIEDAD_SINTETICO",
    "metodo_generacion": "PREPARACION_SINTETICA_MANUAL_130B",
    "modelo_generacion": null,
    "motivo_instantanea": "Fixture sintético de control de propiedad; no representa una inferencia ni atribuye hábitos al propietario.",
    "titulo_instantanea": "Comprueba cómo organizar tus gestiones",
    "recomendacion_ia_id": 29,
    "referencia_contexto": "FIXTURE_130B_MANUAL_AJENO_V1",
    "version_presentacion": "1",
    "prioridad_instantanea": "MEDIA",
    "descripcion_instantanea": "Anota tus próximas gestiones y comprueba qué necesidades debe cubrir cada salida antes de organizarla."
  }
]
```

### Canónica29

```json
{
  "id": 29,
  "titulo": "Acción IA: TR_PLANIFICAR_DESPLAZAMIENTOS / 1",
  "accion_id": "TR_PLANIFICAR_DESPLAZAMIENTOS",
  "prioridad": "MEDIA",
  "descripcion": "Identidad canónica de acción ambiental; contenido individual en la asociación.",
  "categoria_id": 61,
  "version_accion": "1",
  "puntos_recompensa": 0
}
```

### Reto3 y participación3

```json
{
  "reto": {
    "id": 3,
    "tipo": "INDIVIDUAL",
    "activo": true,
    "titulo": "Comprueba cómo organizar tus gestiones",
    "fecha_fin": null,
    "dificultad": null,
    "descripcion": "Anota tus próximas gestiones y comprueba qué necesidades debe cubrir cada salida antes de organizarla.",
    "categoria_id": 61,
    "fecha_inicio": "2026-11-24",
    "institucion_id": null,
    "puntos_recompensa": 0,
    "usuario_recomendacion_ia_id": 30
  },
  "participacion": {
    "id": 3,
    "estado": "activo",
    "reto_id": 3,
    "progreso": 0,
    "usuario_id": 91,
    "fecha_aceptacion": "2026-11-24T13:41:16.910428",
    "fecha_completado": null
  }
}
```

Reto INDIVIDUAL, categoría61, activo, origen30, recompensa0; título/descripcion iguales a snapshot30. Exactamente1 reto vinculado al origen30. Participación3, dueño91, reto3, activo/progreso0/completadoNULL; exactamente1 participación. Todas las fechas de ambos iguales a las referencias originales.

### Usuarios

```json
[
  {
    "id": 91,
    "nivel": 1,
    "eco_puntos": 0
  },
  {
    "id": 92,
    "nivel": 1,
    "eco_puntos": 0
  }
]
```

Usuario92 sigue con contraseña SQL NULL y sin sesiones/hábitos. La composición académica existente autentica solo91/correo de fixture; no se habilitó92 ni se intentó su login. No se guardaron contraseñas, hashes o tokens.

## Conteos y sesiones

| Tabla/conjunto | Esperado | Observado |
|---|---:|---:|
| Usuarios |2|2|
| Asociaciones |3|3|
| Canónicas |1|1|
| Hábitos |3|3|
| Retos |1|1|
| Participaciones |1|1|
| Sesiones |Determinar|4|
| Asociaciones91 |2|2|
| Pendientes91 |1|1|
| Completadas91 |1|1|

Había3 sesiones antes de la autenticación131. Ahora hay4: incremento de1, no un duplicado de recomendación. La consulta de metadatos de sesiones posteriores a la referencia previa al arranque identificó una sesión nueva del91:

```json
[
  {
    "id": 4,
    "activa": true,
    "usuario_id": 91,
    "fecha_inicio": "2026-11-24T13:39:34.415382",
    "fecha_expiracion": "2026-11-24T16:39:34"
  }
]
```

La sesión4 se inició a2026-11-24T13:39:34.415382, compatible con la autenticación autorizada antes de la conversión13:41. No se consultó ni exportó la columna token. No se eliminó ninguna sesión. Fuera de esta sesión y de los efectos autorizados de conversión/completado, no se detectaron cambios adicionales en las filas/conteos examinados; no se declara una auditoría universal de todas las tablas.

## Precisión temporal y elegibilidad

FechaCompletado física30: **2026-11-24T13:49:14.797937**, sin cambio tras los rechazos. FechaGeneracion:2026-11-24T13:23:49.75312. Aceptación física participación3:2026-11-24T13:41:16.910428. Las comparaciones se hicieron a precisión de microsegundos, sin tolerancias ni truncamientos. Son timestamp without time zone, sin sufijo UTC inventado.

Según CapturaContextoAmbientalIAServiceImpl.leer, la asociación propia completada con accionId/versionAccion válida se añade a realizadas, aunque el método sea manual. Por ello TR_PLANIFICAR_DESPLAZAMIENTOS/1 ahora cuenta realizada para91 y una captura futura cambiará su huella/elegibilidad. No se invocó capturar, POST /generar o Qwen3 para demostrarlo. Conservar29 no conserva todo el contexto global después de completar30.

## Conclusión y preservación

La verificación física respalda el cierre técnico acotado de las operaciones de esta ronda. No queda un fallo de datos por reparar. Reto3 no fue completado; no se limpiaron fixtures/sesiones ni se restauraron estados. Código/pruebas/configuración/HU37/índices y Git sin escrituras. Se puede detener manualmente el servidor tras revisar el cierre; el asistente no lo detuvo y no ejecutará automáticamente otro paso.

