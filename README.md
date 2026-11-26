# HuellaGo - Backend

API REST para el seguimiento de la huella de carbono y la promoción de hábitos sostenibles mediante medición ambiental, gamificación y recomendaciones inteligentes.

Proyecto académico de la Universidad Peruana de Ciencias Aplicadas (UPC).

## Tecnologías

- Java 26
- Spring Boot 4.1.1
- Spring Security y JWT
- Spring Data JPA
- PostgreSQL
- Maven
- Swagger UI y OpenAPI
- Apache PDFBox
- Ollama y Qwen3
- JUnit y Mockito

## Funcionalidades

- Registro, autenticación y gestión de usuarios.
- Gestión de hábitos y objetivos ambientales.
- Cálculo, historial y desglose de la huella de carbono.
- Dashboard personal e institucional.
- Retos individuales y colectivos.
- Eco puntos, niveles, recompensas e insignias.
- Recomendaciones ambientales mediante inteligencia artificial.
- Notificaciones y preferencias de privacidad.
- Comunidad y rankings.
- Gestión de instituciones, grupos y campañas.
- Reportes ambientales en PDF y CSV.

## Arquitectura

El proyecto utiliza una arquitectura por capas:

| Paquete | Responsabilidad |
| --- | --- |
| controllers | Endpoints REST |
| dtos | Transferencia de datos |
| entities | Entidades del dominio |
| repositories | Acceso a datos |
| services | Interfaces de negocio |
| serviceimpl | Implementación de servicios |
| security | Autenticación y seguridad |
| exceptions | Manejo de excepciones específicas |

## Requisitos

- JDK 26.
- Maven.
- PostgreSQL.
- Ollama para las recomendaciones IA locales.

## Configuración

Configurar la conexión a PostgreSQL en application.properties y establecer las siguientes variables de entorno:

| Variable | Descripción |
| --- | --- |
| HUELLAGO_DB_PASSWORD | Contraseña de PostgreSQL |
| HUELLAGO_JWT_SECRET | Clave JWT Base64 de al menos 32 bytes |
| HUELLAGO_CORS_ALLOWED_ORIGINS | Orígenes permitidos del frontend |
| HUELLAGO_SWAGGER_ENABLED | Habilitación de Swagger |

Las credenciales deben configurarse localmente y no almacenarse en el repositorio.

## Ejecución

Importar el proyecto Maven en IntelliJ IDEA y ejecutar HuellaGoBackendApplication.

También puede ejecutarse mediante:

    mvn spring-boot:run

Servidor local:

http://localhost:8081

## Inteligencia artificial

Instalar Ollama y descargar el modelo:

    ollama pull qwen3:4b-instruct

Ollama debe estar ejecutándose para generar recomendaciones inteligentes.

Las recomendaciones actuales están destinadas al uso universitario local y no constituyen una certificación ambiental.

## Documentación de la API

- Swagger UI: http://localhost:8081/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8081/v3/api-docs
- OpenAPI YAML: http://localhost:8081/v3/api-docs.yaml

Para acceder a los endpoints protegidos, iniciar sesión mediante POST /auth/login e introducir el JWT obtenido en la opción Authorize de Swagger.

## Idioma de las respuestas

Envía la cabecera `Accept-Language: es-419` o `Accept-Language: en-US`
desde Swagger, Postman o Angular. También se aceptan `es` y `en`.
Sin cabecera o con un idioma no soportado se usa español latinoamericano.
La cabecera aparece como parámetro opcional en Swagger; no cambia rutas,
nombres JSON, estados ni códigos. Los datos aportados por usuarios o terceros
se conservan. Las 30 variantes IA tienen traducciones inglesas controladas por
identidad y huella. Cambiar el idioma de consulta no vuelve a llamar a Ollama
ni modifica las instantáneas españolas guardadas. Las traducciones inglesas
están pendientes de revisión editorial humana y no habilitan `PUBLICABLE`.
Una recomendación histórica sin identidad y texto original verificables
conserva su presentación guardada.

## Datos ambientales externos

Con JWT, `GET /ambiente/calidad-aire?latitud=-12.0464&longitud=-77.0428`
consulta el modelo de calidad del aire de Open-Meteo/CAMS. La respuesta es
orientativa y no representa una medición local ni emisiones de CO2.

`GET /ambiente/puntos-sostenibles?latitud=-12.0464&longitud=-77.0428&radioMetros=3000&tipo=RECICLAJE`
consulta Geoapify Places con datos de OpenStreetMap. Los otros tipos son
`ESTACIONAMIENTO_BICICLETA` y `ALQUILER_BICICLETA`. Presenta la atribución
Geoapify y OpenStreetMap/ODbL incluida en la respuesta. Los metadatos opcionales
pueden faltar; la capacidad instalada no representa disponibilidad en tiempo real.

Geoapify está deshabilitado por defecto. Para probarlo, configura en IntelliJ las
variables de entorno `HUELLAGO_GEOAPIFY_ENABLED=true` y
`HUELLAGO_GEOAPIFY_API_KEY` con la clave privada de tu proyecto. No guardes la
clave en el repositorio. Se aplican hasta 20 resultados, 2 solicitudes externas
por segundo, caché de 10 minutos y un presupuesto configurable por instancia
de 1000 solicitudes externas al día (`HUELLAGO_GEOAPIFY_DAILY_LIMIT`).
Los aciertos de caché no consumen el presupuesto local. El contador en memoria
se reinicia al reiniciar el backend y no coordina varias instancias; supervisa
también la cuota de Geoapify. El tiempo de espera se configura con
`HUELLAGO_GEOAPIFY_READ_TIMEOUT_SECONDS`.

Open-Meteo sigue habilitado para uso universitario local y puede deshabilitarse
con `HUELLAGO_OPEN_METEO_ENABLED=false`. Sus ajustes existentes son
`HUELLAGO_OPEN_METEO_URL`, `HUELLAGO_AMBIENTE_CONNECT_TIMEOUT_SECONDS` y
`HUELLAGO_AMBIENTE_READ_TIMEOUT_SECONDS`.

## Base de datos

PostgreSQL almacena la información del sistema.

El archivo HuellaGo_Datos_Prueba_Completos.sql contiene datos sintéticos para demostraciones en una base de datos de desarrollo.

## Pruebas

Las pruebas automatizadas se encuentran en src/test y permiten verificar las funcionalidades, la seguridad y los contratos de la API.

Las pruebas de integración real requieren servicios configurados y una base de datos de pruebas independiente.

## Equipo

HuellaGo - Grupo 5

Universidad Peruana de Ciencias Aplicadas (UPC).

Arquitectura de Aplicaciones Web - 202620.
