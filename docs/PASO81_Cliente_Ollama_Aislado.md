# Paso 81: cliente Ollama aislado

## Alcance

Se incorpora GeneradorRecomendacionesIAService y un adaptador RestClient para
POST /api/chat. No se conecta con RecomendacionService, controllers, listeners,
JWT, repositorios o entidades. Crear los beans no realiza ninguna solicitud.
No hay endpoints nuevos ni lectura/escritura PostgreSQL. No se modifica pom.xml.

El cliente recibe ContextoRecomendacionesIADTO (datos sintéticos en esta etapa),
sin identidad, correo, credenciales o entidades. Cada categoría es opcional, pero
las presentes deben contener datos válidos y debe existir al menos una.
Los registros anidados representan transporte, energía, alimentación y residuos.
consumoKwhDiario es opcional y solo corresponde a Electricidad.

El adaptador pide contenido al modelo, no utiliza acciones alternativas fijas.
El resultado interno es una lista inmutable de RecomendacionGeneradaIADTO:
categoria, titulo, descripcion y motivo. No incluye IDs, puntos, estado ni prioridad.
Los datos de este paso no proceden de usuarios ni de bases de datos reales.

## Configuración externa

No editar application.properties. Se admiten propiedades externas de Spring y las
variables de entorno equivalentes. Los tiempos se expresan como Duration ISO-8601.

| Propiedad | Variable de entorno | Predeterminado |
|---|---|---|
| huellago.ollama.base-url | HUELLAGO_OLLAMA_BASE_URL | http://localhost:11434 |
| huellago.ollama.model | HUELLAGO_OLLAMA_MODEL | qwen3:4b-instruct |
| huellago.ollama.connect-timeout | HUELLAGO_OLLAMA_CONNECT_TIMEOUT | PT3S |
| huellago.ollama.read-timeout | HUELLAGO_OLLAMA_READ_TIMEOUT | PT120S |
| huellago.ollama.max-response-bytes | HUELLAGO_OLLAMA_MAX_RESPONSE_BYTES | 65536 |

URL HTTP/HTTPS de servidor, sin credenciales, query, fragmento ni ruta adicional.
Los redireccionamientos no se siguen. La URL procede de configuración del operador,
no de la entrada ambiental. Los tiempos deben estar entre 1 ms y 10 minutos; el
límite de respuesta, entre 1024 bytes y 1 MiB. Una configuración inválida se rechaza
sin reproducir sus valores en el mensaje de error.

Estos límites técnicos no son factores ni umbrales ambientales. Los tiempos reales
deben calibrarse después con el modelo y hardware instalados; todavía no se verifican.

## Solicitud HTTP

El cliente envía model, stream:false, messages y format con JSON Schema.
El esquema exige exactamente una recomendación por cada categoría presente,
enum de categorías solicitadas, cuatro campos obligatorios por recomendación y
additionalProperties:false. El servidor valida además la unicidad de categorías.

Ejemplo ilustrativo de solicitud con datos ficticios (las instrucciones del cliente
incluyen también las prohibiciones de inventar datos y afirmaciones científicas):

```json
{
  "model":"qwen3:4b-instruct",
  "stream":false,
  "format":{
    "type":"object",
    "additionalProperties":false,
    "required":["recomendaciones"],
    "properties":{
      "recomendaciones":{
        "type":"array","minItems":4,"maxItems":4,
        "items":{
          "type":"object","additionalProperties":false,
          "required":["categoria","titulo","descripcion","motivo"],
          "properties":{
            "categoria":{"type":"string","enum":["Transporte","Energía","Alimentación","Residuos"]},
            "titulo":{"type":"string","minLength":1,"maxLength":160},
            "descripcion":{"type":"string","minLength":1,"maxLength":1000},
            "motivo":{"type":"string","minLength":1,"maxLength":1000}
          }
        }
      }
    }
  },
  "messages":[
    {"role":"system","content":"Genera una recomendación por categoría, relacionada con los datos. Devuelve únicamente JSON. No inventes factores, ahorros ni referencias científicas."},
    {"role":"user","content":"{\"Transporte\":{\"medio\":\"Bus\",\"kmSemana\":80,\"diasSemana\":5},\"Energía\":{\"vivienda\":\"Casa\",\"personas\":3,\"fuente\":\"Electricidad\",\"consumoKwhDiario\":12},\"Alimentación\":{\"tipo\":\"Mixta\"},\"Residuos\":{\"plasticos\":\"Medio\",\"reciclas\":false}}"}
  ]
}
```

## Respuesta y validaciones

Primero se deserializa la envoltura de Ollama. Se requiere model coincidente,
done:true, message.role=assistant y message.content textual no vacío.
Si done_reason está presente debe ser stop. Un cuerpo con error se rechaza,
incluso si el HTTP fue 200. No se interpreta message.thinking como contenido.

Después se deserializa message.content como otro JSON y se comprueban categoría,
campos, cantidad y tamaños. Se rechazan categorías desconocidas, duplicadas,
no solicitadas o ausentes; no se completa el resultado mediante reglas.

Ejemplo de envoltura simulada para una solicitud parcial de Transporte:

```json
{
  "model":"qwen3:4b-instruct",
  "done":true,
  "done_reason":"stop",
  "message":{
    "role":"assistant",
    "content":"{\"recomendaciones\":[{\"categoria\":\"Transporte\",\"titulo\":\"Acción ficticia del test\",\"descripcion\":\"Contenido simulado exclusivamente para probar HTTP.\",\"motivo\":\"Motivo ficticio del test.\"}]}"
  }
}
```

Esta respuesta es un fixture, no una salida real obtenida del modelo. Una solicitud
con las cuatro categorías requiere cuatro elementos distintos, uno por categoría.

Se conserva el orden de categorías del contexto, aunque el modelo cambie el orden.
Se rechazan texto fuera del JSON, claves duplicadas, UTF-8 malformado y JSON anidado
excesivamente. Entrada HTTP máxima: 16 KiB; respuesta limitada por bytes antes de parsear.
Título máximo: 160 unidades UTF-16; descripción y motivo: 1000 cada uno.
Estos tamaños son internos del cliente, no garantizan compatibilidad con futuras columnas.

OllamaClientException expone tipos internos: CONTEXTO_INVALIDO, RESPUESTA_INVALIDA,
ERROR_HTTP, TIEMPO_AGOTADO y NO_DISPONIBLE. ERROR_HTTP conserva únicamente el código
de estado y un mensaje controlado, sin cuerpo del proveedor. No hay mapeo a un nuevo
contrato HTTP público en este paso. No hay reintentos ni fallback determinista.

## Pruebas

Solo JUnit y MockRestServiceServer, sin ApplicationContext de Spring Boot ni datasource.
Se comprueban protocolo, esquema, deserialización doble, categorías completas/parciales,
configuración, privacidad del contexto, respuestas erróneas, límites y fallos de transporte.
Los timeouts son excepciones simuladas: no certifican tiempos de inferencia reales.
No se ejecuta inferencia real, health check ni petición a localhost:11434 desde Java.
Tampoco se ejecuta la regresión anterior completa; la selección se limita a los dos
tests nuevos: OllamaRecomendacionesServiceImplTest y OllamaConfigurationTest.

Resultado final: 87 pruebas ejecutadas y aprobadas (63 del cliente y 24 de
configuración), 0 fallos, 0 errores y 0 omitidas. Maven test: BUILD SUCCESS.
La ejecución anterior de 81 casos se amplió con límites de puerto, timeout JDK,
configuración de URL/modelo, UTF-8 inválido y separación de thinking/metadatos.
No sumar ambas ejecuciones como casos distintos. Durante la compilación inicial
se corrigieron una expresión del esquema y el import JsonFactory de Jackson 3,
únicamente en archivos nuevos; esos intentos no llegaron a ejecutar pruebas.

## Pendientes

- Probar explícitamente el cliente contra el modelo instalado, tras autorización.
- Evaluar calidad, alucinaciones y latencia. JSON válido no garantiza veracidad.
- Revisar los límites internos para la futura persistencia.
- Diseñar por separado integración con hábitos reales, JWT, almacenamiento, generaciones,
  concurrencia y actualización dinámica fuera de BEFORE_COMMIT.
- El módulo público actual sigue usando REGLAS_DETERMINISTAS: este adaptador todavía
  no está conectado y no permite declarar HU31/HU37 completas como Recomendaciones IA.
