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

- JDK 26 y `JAVA_HOME` apuntando a ese JDK. El proyecto compila a Java 21 mediante Maven.
- IntelliJ IDEA con soporte Maven, Maven instalado o el Wrapper incluido (`mvnw.cmd` en Windows, `./mvnw` en macOS/Linux).
- PostgreSQL 18 para la base de datos. Docker Compose es opcional.
- Ollama y el modelo `qwen3:4b-instruct` para generar recomendaciones IA; el resto de la API puede arrancar sin el proveedor activo.

## Descargar y abrir el proyecto

Clona el repositorio con `git clone https://github.com/Huellago-Grupo5/HuellaGo-Backend.git` o descarga el ZIP desde GitHub y descomprímelo. En IntelliJ, usa **File > Open** sobre la carpeta que contiene `pom.xml`, acepta la importación Maven y selecciona JDK 26 como Project SDK y JDK del runner Maven. No es necesario crear perfiles Spring.

## Configuración mínima

1. Crea una base PostgreSQL vacía llamada `huellagodb_hu29_test`. En una instalación nativa, crea un rol de desarrollo con permisos de propietario sobre **esa base nueva**. No reutilices una base ajena ni ejecutes migraciones sobre una base con datos sin respaldo. El nombre es importante: la migración manual V5 y el script integral comprueban expresamente ese destino.
2. Copia `.env.example` a `.env` y completa **en privado** `HUELLAGO_DB_PASSWORD` y `HUELLAGO_JWT_SECRET`. Ajusta `HUELLAGO_DB_URL` y `HUELLAGO_DB_USERNAME` al puerto y rol reales. Sin Docker, la URL suele ser `jdbc:postgresql://localhost:5432/huellagodb_hu29_test`.
3. En **Run > Edit Configurations**, crea una configuración **Application** para `com.huellago.backend.HuellaGoBackendApplication`, con JDK 26 y el módulo del proyecto. En **Environment variables**, introduce como mínimo `HUELLAGO_DB_URL`, `HUELLAGO_DB_USERNAME`, `HUELLAGO_DB_PASSWORD` y `HUELLAGO_JWT_SECRET`. IntelliJ y Spring Boot **no importan automáticamente `.env`**. Usa los valores privados del archivo solo en tu equipo; nunca lo publiques.
4. Genera una clave JWT Base64 de al menos 32 bytes una sola vez, guárdala de forma privada y reutilízala después de reiniciar. En PowerShell puedes generar una y copiarla al portapapeles sin imprimirla: `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)) | Set-Clipboard`. No guardes la clave en Git.

`application.properties` conserva los valores habituales: PostgreSQL en `localhost:5432`, base `huellagodb_hu29_test`, rol `postgres`, servidor en `127.0.0.1:8081`, Ollama habilitado y modelo `qwen3:4b-instruct`. Las variables de entorno sustituyen solo los valores indicados; no edites el archivo compartido para poner contraseñas. Para un arranque sin inferencia configura `HUELLAGO_OLLAMA_ENABLED=false`; con la configuración habitual, Ollama solo se consulta al pedir una generación IA.

En PowerShell también puedes asignar las mismas variables a **la sesión actual** (`$env:NOMBRE='valor privado'`) y ejecutar `.\mvnw.cmd spring-boot:run` o `mvn spring-boot:run`. El Wrapper puede descargar Maven en el repositorio estándar del usuario; IntelliJ incluye Maven si prefieres usarlo. Estas variables de PowerShell no se transfieren automáticamente a una configuración Run independiente de IntelliJ.

En la **primera instalación de una base vacía**, el arranque habitual crea/actualiza tablas con Hibernate (`ddl-auto=update`) e inicializa los catálogos de `data.sql` (`spring.sql.init.mode=always`). Después de ese primer arranque, detén el backend y aplica manualmente en pgAdmin, en orden, `db/migration/V1` a `V5` sobre la base nueva para incorporar las restricciones adicionales; estos archivos **no se ejecutan solos**. Reinicia el backend y verifica Swagger. No cambies estos ajustes para seguir esta guía. El cierre programado de retos colectivos está activo por defecto y puede escribir al arrancar una base con retos vencidos; para trabajar con datos históricos de prueba sin ese efecto usa temporalmente `HUELLAGO_CIERRE_COLECTIVO_ENABLED=false`.

## PostgreSQL y Ollama con Docker Compose

Docker Compose crea o reutiliza volúmenes persistentes propios de este proyecto. Expone PostgreSQL solo en `127.0.0.1:55432` y Ollama solo en `127.0.0.1:11435`, evitando los puertos nativos habituales. Usa el nombre de base `huellagodb_hu29_test` dentro del contenedor, pero **no toca** la instalación PostgreSQL del host. En `.env` establece un `HUELLAGO_DB_PASSWORD` privado y conserva `HUELLAGO_DB_USERNAME=huellago_local`, `HUELLAGO_DB_URL=jdbc:postgresql://localhost:55432/huellagodb_hu29_test` y `HUELLAGO_OLLAMA_BASE_URL=http://localhost:11435`.

```powershell
docker compose config --quiet
docker compose --profile ia up -d
docker compose exec ollama ollama pull qwen3:4b-instruct
docker compose exec ollama ollama list
```

Para iniciar **solo PostgreSQL** usa `docker compose up -d postgres`. No ejecutes a la vez un Ollama nativo en el puerto publicado elegido. Si `55432` o `11435` están ocupados, cambia `HUELLAGO_COMPOSE_PG_PORT` o `HUELLAGO_COMPOSE_OLLAMA_PORT` en `.env` y ajusta las URL de Java. `docker compose stop` detiene los contenedores y conserva los volúmenes; evita `down -v` si deseas conservar sus datos. El modelo se descarga bajo demanda y requiere espacio y memoria suficientes.

## Inteligencia artificial

Sin Docker, instala Ollama, ejecútalo localmente y descarga el modelo:

```powershell
ollama pull qwen3:4b-instruct
ollama list
```

La URL habitual es `http://localhost:11434`. Para Docker usa la URL del apartado anterior. El motor selecciona acciones del catálogo autorizado; Java valida y persiste. La modalidad actual es universitaria local, no `PUBLICABLE`.

## Integraciones opcionales y configuración completa

| Integración | Variables | Uso |
| --- | --- | --- |
| Open-Meteo | `HUELLAGO_OPEN_METEO_ENABLED` (predeterminado `true`), `HUELLAGO_OPEN_METEO_URL` | Calidad del aire orientativa; no requiere clave. |
| Geoapify | `HUELLAGO_GEOAPIFY_ENABLED=true`, `HUELLAGO_GEOAPIFY_API_KEY` | Puntos sostenibles; cada integrante obtiene su propia clave y respeta su cuota. |
| Correo Gmail | `HUELLAGO_RECOVERY_ENABLED=true`, `HUELLAGO_RECOVERY_FROM`, `HUELLAGO_RECOVERY_RESET_URL`, `SPRING_MAIL_HOST=smtp.gmail.com`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Recuperación de contraseña. Configura una **contraseña de aplicación** de Google para la cuenta remitente, con verificación en dos pasos; no utilices la contraseña normal. La URL de recuperación debe señalar la pantalla del frontend que recibe `?token=...`. |
| Frontend | `HUELLAGO_CORS_ALLOWED_ORIGINS` | Lista separada por comas de orígenes autorizados, sin barra final. |
| Swagger | `HUELLAGO_SWAGGER_ENABLED` (predeterminado `true`) | Documentación local. |

Para Gmail también se configuran `HUELLAGO_SMTP_AUTH`, `HUELLAGO_SMTP_STARTTLS_ENABLE` y `HUELLAGO_SMTP_STARTTLS_REQUIRED` (predeterminados `true`). El servidor exige TLS para SMTP remoto. La recuperación permanece deshabilitada si no configuras sus variables. Las variables de tiempo de espera y cuota de proveedores figuran en `.env.example`. No introduzcas claves API ni contraseñas de correo en `application.properties`, Swagger o Git.

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

PostgreSQL almacena la información del sistema. Si utilizas PostgreSQL nativo, en pgAdmin crea un rol de desarrollo con **Login** y una contraseña privada; luego crea una base `huellagodb_hu29_test` cuyo propietario sea ese rol. Configura el host, puerto, rol y contraseña correspondientes en IntelliJ. En Compose, el contenedor crea la base y el rol al inicializar por primera vez su volumen. Cambiar posteriormente esas variables no renombra una base ya creada.

Para datos sintéticos, después del primer arranque y las migraciones, detén el backend y abre [HuellaGo_Datos_Prueba_Completos.sql](HuellaGo_Datos_Prueba_Completos.sql) en **pgAdmin > Query Tool** de `huellagodb_hu29_test`. Verifica la conexión seleccionada y ejecuta el archivo completo una sola vez. El script exige ese nombre de base, usa una transacción y reconoce una carga previa completa para evitar duplicarla. Contiene credenciales **ficticias conocidas** para pruebas: no lo ejecutes en producción ni sobre datos que deban preservarse. No es necesario para registrar usuarios nuevos y probar los endpoints básicos.

## Problemas frecuentes

| Síntoma | Comprobación |
| --- | --- |
| Falta `HUELLAGO_JWT_SECRET` o el JWT deja de servir tras reiniciar | Usa la misma clave Base64 privada y estable de al menos 32 bytes en cada arranque. |
| Error de autenticación o conexión PostgreSQL | Confirma `HUELLAGO_DB_URL`, rol, contraseña y puerto. Compose usa `55432` por defecto; PostgreSQL nativo suele usar `5432`. |
| Puerto ocupado | No inicies otro backend en `8081`. Cambia únicamente los puertos publicados de Compose si `55432` o `11435` están ocupados; ajusta las URL de Java. |
| Error de tablas o catálogos ausentes | En una base nueva deja terminar el primer arranque; `data.sql` se ejecuta automáticamente. Aplica las migraciones manuales en orden antes de cargar el script integral. |
| Recomendaciones IA no disponibles | Comprueba `ollama list`, el modelo `qwen3:4b-instruct`, la URL local y `HUELLAGO_OLLAMA_ENABLED`. El backend no descarga modelos automáticamente. |
| Geoapify no disponible | Comprueba `HUELLAGO_GEOAPIFY_ENABLED=true`, tu clave privada y la cuota del proveedor. |
| Recuperación de contraseña responde 503 | Comprueba la activación, la cuenta remitente, la contraseña de aplicación, STARTTLS y que `HUELLAGO_RECOVERY_RESET_URL` apunte a una pantalla real del frontend. |
| CORS en Angular | Configura `HUELLAGO_CORS_ALLOWED_ORIGINS` con el origen exacto del frontend. JWT sigue siendo necesario. |
| `mvnw.cmd` usa otro JDK | Establece `JAVA_HOME` al JDK 26 antes de ejecutar el Wrapper; IntelliJ tiene un JDK de Maven Runner independiente. |

## Pruebas

Las pruebas automatizadas se encuentran en src/test y permiten verificar las funcionalidades, la seguridad y los contratos de la API.

Las pruebas de integración real requieren servicios configurados y una base de datos de pruebas independiente.

## Equipo

HuellaGo - Grupo 5

Universidad Peruana de Ciencias Aplicadas (UPC).

Arquitectura de Aplicaciones Web - 202620.
