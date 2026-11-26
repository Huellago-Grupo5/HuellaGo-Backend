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

1. Para PostgreSQL nativo, usa la base `huellagodb` y un rol de desarrollo con permisos de propietario. Las migraciones y datos de prueba también admiten `huellagodb_hu29_test`, que es el nombre usado por Docker Compose. No apuntes a una base ajena ni ejecutes migraciones sobre datos importantes sin respaldo.
2. Copia `.env.example` a `.env` y completa **en privado** `HUELLAGO_DB_PASSWORD`. Ajusta `HUELLAGO_DB_URL` y `HUELLAGO_DB_USERNAME` al puerto, nombre de base y rol reales. El valor predeterminado nativo es `jdbc:postgresql://localhost:5432/huellagodb`; Compose configura su propia URL al puerto `55432` y la base `huellagodb_hu29_test`. En desarrollo local no hace falta proporcionar `HUELLAGO_JWT_SECRET`: el backend crea una clave segura y persistente en `~/.huellago/jwt-secret`.
3. En **Run > Edit Configurations**, crea una configuración **Application** para `com.huellago.backend.HuellaGoBackendApplication`, con el JDK indicado y el módulo del proyecto. En **Environment variables**, introduce `HUELLAGO_DB_URL`, `HUELLAGO_DB_USERNAME` y `HUELLAGO_DB_PASSWORD`. IntelliJ y Spring Boot **no importan automáticamente `.env`**. Usa los valores privados del archivo solo en tu equipo; nunca lo publiques.
4. Para producción, configura `HUELLAGO_JWT_SECRET` con una clave Base64 segura de al menos 32 bytes y mantenla estable entre reinicios. Es obligatoria con el perfil `prod` o `production`; no la guardes en Git. En desarrollo local se genera y conserva automáticamente.

`application.properties` usa PostgreSQL en `localhost:5432`, base predeterminada `huellagodb`, rol `huellago_local`, servidor en `127.0.0.1:8081`, Ollama habilitado y modelo `qwen3:4b-instruct`. `HUELLAGO_DB_URL`, `HUELLAGO_DB_USERNAME` y `HUELLAGO_DB_PASSWORD` permiten que cada integrante indique la dirección y credenciales propias; no edites el archivo compartido para poner contraseñas. Para un arranque sin inferencia configura `HUELLAGO_OLLAMA_ENABLED=false`; con la configuración habitual, Ollama solo se consulta al pedir una generación IA.

En PowerShell también puedes asignar las mismas variables a **la sesión actual** (`$env:NOMBRE='valor privado'`) y ejecutar `.\mvnw.cmd spring-boot:run` o `mvn spring-boot:run`. El Wrapper puede descargar Maven en el repositorio estándar del usuario; IntelliJ incluye Maven si prefieres usarlo. Estas variables de PowerShell no se transfieren automáticamente a una configuración Run independiente de IntelliJ.

En el primer arranque, Hibernate crea o actualiza las tablas y, al terminar, el inicializador PostgreSQL del proyecto aplica automáticamente `db/migration/V1` a `V5` en orden y carga `data.sql`. Este inicializador entiende bloques `DO $$ ... $$`, ejecuta cada migración en una transacción y registra su versión y checksum en `huellago_schema_migrations`. Si una versión ya consta como aplicada, se omite; si su archivo cambió, el arranque se detiene para evitar una actualización inesperada. `data.sql` se vuelve a evaluar en cada inicio dentro de una transacción y sus comprobaciones existentes evitan duplicar los registros de prueba. La carga contiene datos sintéticos de HuellaGo; úsala solo en la base de desarrollo indicada y conserva cualquier respaldo de bases con datos. No hay que ejecutar SQL de inicialización manualmente. El cierre programado de retos colectivos está activo por defecto y puede escribir al arrancar una base con retos vencidos; para trabajar con datos históricos de prueba sin ese efecto usa temporalmente `HUELLAGO_CIERRE_COLECTIVO_ENABLED=false`.

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

PostgreSQL almacena la información del sistema. Si utilizas PostgreSQL nativo, en pgAdmin crea un rol de desarrollo con **Login** y una contraseña privada; luego usa `huellagodb` como base y asígnale ese rol como propietario. Configura el host, puerto, rol y contraseña mediante `HUELLAGO_DB_URL`, `HUELLAGO_DB_USERNAME` y `HUELLAGO_DB_PASSWORD`. En Compose, el contenedor crea la base `huellagodb_hu29_test` y el rol al inicializar por primera vez su volumen. Cambiar posteriormente esas variables no renombra una base ya creada.

`data.sql` carga automáticamente los catálogos y los datos sintéticos integrales durante el arranque. La carga permite los destinos `huellagodb` y `huellagodb_hu29_test`, contiene credenciales ficticias conocidas y no debe utilizarse en producción. En una base que ya tenga registros, las migraciones conservan las filas existentes; la carga puede agregar sus filas de prueba si todavía no están. No es necesario ejecutar SQL manualmente para iniciar el backend.

## Problemas frecuentes

| Síntoma | Comprobación |
| --- | --- |
| El JWT deja de servir tras reiniciar | En desarrollo usa el mismo usuario del sistema para conservar `~/.huellago/jwt-secret`; en producción configura la misma `HUELLAGO_JWT_SECRET` segura en cada arranque. |
| Error de autenticación o conexión PostgreSQL | Confirma `HUELLAGO_DB_URL`, rol, contraseña y puerto. Compose usa `55432` por defecto; PostgreSQL nativo suele usar `5432`. |
| Puerto ocupado | No inicies otro backend en `8081`. Cambia únicamente los puertos publicados de Compose si `55432` o `11435` están ocupados; ajusta las URL de Java. |
| Error de tablas o catálogos ausentes | Revisa el log de inicio, la conexión y los permisos del rol. Hibernate crea primero las tablas; luego se aplican las migraciones y `data.sql` automáticamente. |
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
