# PASO 111 — Revisión humana posterior a la única ronda real

Se ejecutaron exactamente dos peticiones POST /api/chat: A y B. C fue local.
No hubo reintentos ni modificaciones de producción. Disponibilidad comprobada
previamente mediante GET /api/tags; modelo qwen3:4b-instruct.

## Resultado técnico

| Escenario | HTTP | ms HTTP | ms orquestación | bytes petición | Resultado |
|---|---:|---:|---:|---:|---|
| A | 200 | 6289 | 6474 | 7807 | PROPONER TR_PLANIFICAR_DESPLAZAMIENTOS / 1 |
| B | 200 | 3908 | 3965 | 6349 | PROPONER EN_REVISAR_REGISTRO_HOGAR / 1 |
| C | Sin llamada | — | 21 | Sin petición | SIN_PROPUESTA |

A y B: ACEPTADA_ESTRUCTURALMENTE sin motivos de rechazo. Ambos devolvieron
parametros={} y textos null. La presentación local utilizó SIN_ENFOQUE.
B ejercitó evidencia real de 12.345 con ámbito HOGAR, sin conversión por persona.
Energía en A y Alimentación en B permanecieron completas y excluidas del alcance.

## Evaluación humana, no certificación ambiental

- A: el paso solicita anotar las gestiones y comprobar sus necesidades. Es observable,
  coincide con Auto y no inventa destinos, horarios ni posibilidades de combinación.
  El motivo utiliza los datos sintéticos registrados y reconoce lo desconocido.
  Su utilidad depende de que existan gestiones futuras; no demuestra reducción de emisiones.
- B: el paso solicita comprobar procedencia, periodo y cobertura de la medición original.
  Respeta el consumo del hogar y no diagnostica intensidad ni afirma ahorro. Es una
  acción de calidad de datos; no debe presentarse como una reducción ambiental conseguida.
- Ambos textos provienen del presentador controlado, no de redacción libre de Qwen3.
  La selección sí procede de HTTP real. No hubo personalización mediante parámetros.
- B tenía una única candidata: demuestra propuesta/abstención y evidencias dentro
  del alcance, no selección comparativa entre varias acciones energéticas.
- Todas las vistas previas siguen BORRADOR, PENDIENTE_REVISION y publicable=false.

## Pruebas

Regresión previa: 374 casos reportados, 373 aprobados y 1 omitido correspondiente
a la clase real deshabilitada por defecto; 0 fallos y 0 errores, BUILD SUCCESS.
Ronda habilitada: 3 escenarios aprobados, 0 fallos, 0 errores y 0 omitidos;
BUILD SUCCESS. No se repitió la ronda.

Los originales HTTP, JSON del modelo y resultados locales están separados en
esta carpeta. No se consultaron PostgreSQL ni datos personales. La conformidad
académica y aprobación editorial siguen pendientes.
