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