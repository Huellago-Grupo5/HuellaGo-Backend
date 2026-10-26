# HU05, HU03, HU04 y HU45: cuentas y recuperación segura

Implementación del backend de Arquitectura Web según los criterios del documento
`HuellaGo_IHC_56_HU_Criterios_Auditoria.md`. No incluye interfaz visual.

Actualización puntual HU05: consultar [límites de frecuencia](HU05_Limites_Frecuencia.md).
Las nuevas solicitudes solo sustituyen enlaces anteriores cuando son admitidas
por las cuotas; los rechazos 429 preservan el token previo.

## HU05: recuperación de contraseña

El diseño anterior devolvía un JWT de recuperación directamente al solicitante.
Se retiró esa respuesta y se eliminaron los métodos de generación/validación del
JWT de recuperación. Ningún JWT antiguo puede utilizarse para restablecer una clave.

Arquitectura: `UsuarioController → UsuarioServiceImpl → RecuperacionContrasenaService
→ RecuperacionContrasenaServiceImpl → RecuperacionContrasenaRepository`, con
`CorreoRecuperacionService → CorreoRecuperacionSmtp` para entrega SMTP externa.

- `POST /auth/recuperar-contrasena`, JSON `{"correo":"usuario@example.invalid"}`.
- HTTP 200: `{"mensaje":"El enlace fue aceptado por el servicio de correo para su envío"}`.
  No contiene token, ID, correo, contraseña ni hash. La aceptación SMTP no prueba
  entrega final al buzón: rechazos posteriores/bounces dependen del proveedor.
- HTTP 404 para correo inexistente, conforme al criterio original. Esta respuesta
  revela existencia de cuenta; aplicar limitación de solicitudes en despliegue.
- HTTP 400 por solicitud inválida. HTTP 503 si falta configuración o falla SMTP.
  No existe respuesta ficticia de envío exitoso cuando falta el servicio.
- `POST /auth/restablecer-contrasena`, JSON con `token` recibido exclusivamente
  por correo y `nuevaContrasena` conforme a la política. HTTP 204 al completar.
- Token inexistente, manipulado, expirado, usado o JWT legado: HTTP 400 genérico.

Se generan 32 bytes mediante `SecureRandom`, codificados Base64 URL sin padding
(43 caracteres). Solo se persiste SHA-256 hexadecimal, destino, usuario y fechas.
Expira a los 15 minutos. Una nueva solicitud sustituye las anteriores del usuario.
La nueva contraseña se cifra con BCrypt; no se guarda el secreto de recuperación.

El restablecimiento bloquea primero el usuario y después la recuperación, vuelve a
leer los datos, consume el token y revoca todas las sesiones en una transacción.
Un cambio de correo invalida sus recuperaciones. Login, perfil y recuperación
comparten el bloqueo del usuario para impedir sesiones nuevas con claves antiguas
durante un reset. Dos consumos simultáneos no pueden completar ambos.

La solicitud guarda y envía dentro de una transacción con tiempos SMTP acotados.
Si falla el envío se revierte la nueva recuperación y se conserva la anterior.
SMTP no participa en la transacción PostgreSQL: si SMTP acepta el mensaje y luego
falla el commit, el enlace recibido no será utilizable. Se debe solicitar otro.
No hay cola/outbox ni reintentos automáticos que puedan duplicar entregas.

### Configuración externa de correo

No se modificó `application.properties`. El servicio está deshabilitado por defecto.
No se incluyeron servidores, credenciales ni claves reales. Configurar externamente:

| Variable | Uso |
| --- | --- |
| `HUELLAGO_RECOVERY_ENABLED` | `true` para habilitar expresamente |
| `HUELLAGO_RECOVERY_FROM` | Remitente autorizado por el proveedor |
| `HUELLAGO_RECOVERY_RESET_URL` | URL HTTPS absoluta de la futura pantalla de reset, sin query/fragmento |
| `SPRING_MAIL_HOST` | Servidor SMTP proporcionado por el equipo |
| `SPRING_MAIL_PORT` | Puerto indicado por el proveedor |
| `SPRING_MAIL_USERNAME` | Usuario SMTP, cuando lo requiera el proveedor |
| `SPRING_MAIL_PASSWORD` | Secreto gestionado externamente |

Configurar además mediante configuración externa Spring:

```properties
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
```

Para SMTP sobre TLS implícito, configurar `mail.smtp.ssl.enable=true` según el
proveedor. Se rechaza SMTP remoto sin TLS requerido; HTTP para el enlace solo se
admite en localhost/127.0.0.1. Timeouts predeterminados de conexión/lectura/escritura:
5 segundos si no se configuraron; debug JavaMail desactivado para evitar divulgar
mensajes/tokens. No activar trazas de payloads HTTP, credenciales o parámetros SQL.

Las pruebas simulan el emisor y jamás envían correos reales. La entrega real queda
pendiente de configurar y verificar por el equipo. La integración utiliza el soporte
oficial de Spring: https://docs.spring.io/spring-boot/4.0/reference/io/email.html.

## HU03: registro

`POST /auth/register` conserva el contrato de datos personales y respuesta pública
HTTP 201. No inicia una sesión implícita; el cliente puede llamar a login después.

- Nombres/apellidos: obligatorios, sin espacios exteriores, 2–80 caracteres,
  letras Unicode y separadores habituales (espacio, punto, apóstrofo, guion).
- Correo: obligatorio, formato email, máximo 120 caracteres; se normaliza con
  `strip()` y minúsculas `Locale.ROOT` en construcción/deserialización y servicio.
- Contraseña: 12–72 caracteres, máximo 72 bytes UTF-8, al menos una mayúscula,
  minúscula, número y símbolo; sin espacios ni caracteres de control.
- Se conserva la contraseña sin recortarla. BCrypt no recibe claves truncadas.
- Bean Validation en controller y servicio: llamadas internas tampoco evaden reglas.
- Correo duplicado: HTTP 409. Índice único `lower(btrim(correo))` protege carreras
  entre registro y edición. Se usa `saveAndFlush` para detectar conflictos.
- Los campos internos (puntos, nivel, preferencias, fechas) se inicializan en servidor.

Ejemplo estructural de solicitud (sustituir la contraseña por una entrada válida):

```json
{"nombres":"Ana","apellidos":"Pérez","correo":"ana@example.invalid","contrasena":"<contraseña elegida por el usuario>"}
```

## HU04: login y sesiones

Arquitectura: `UsuarioController → AccesoCuentaService → AccesoCuentaServiceImpl`.
`POST /auth/login` conserva `correo` y `contrasena`. Valida campos obligatorios y
correo; no aplica retroactivamente la política de nuevas claves a contraseñas
legadas, pero limita el tamaño compatible con BCrypt. Credenciales incorrectas: 401.

Autentica mediante `AuthenticationManager`/Spring Security y registra una sesión
persistida. Los JWT tienen firma HS256, expiración de tres horas y `jti` aleatorio
único. La clave fija anterior fue retirada.

`HUELLAGO_JWT_SECRET` permite configurar una clave Base64 de al menos 32 bytes
aleatorios. No compartirla en Git ni usar una contraseña humana como clave.
Sin configuración se genera una clave criptográfica efímera por instancia:
reiniciar invalida JWT anteriores. Para persistencia entre reinicios o varias
instancias es necesario configurar la misma clave segura externamente.

La respuesta agrega campos sin retirar los existentes:

```json
{
  "token":"<JWT de acceso>",
  "id":123,
  "correo":"ana@example.invalid",
  "configuracionInicialCompleta":false,
  "categoriasPendientes":["Transporte","Energía","Alimentación","Residuos"]
}
```

La configuración se deriva de la presencia persistida de los nueve campos de
hábitos, con categoría, valor y unidad, sin crear un flag ficticio de onboarding.
Este campo indica datos iniciales presentes, no certifica el cálculo de emisiones.
No se implementa navegación ni se almacena el avance de diapositivas introductorias.
Las categorías sin tildes también se reconocen. El cliente decide la pantalla.

`POST /auth/logout` y `DELETE /sesiones/actual` mantienen su contrato y validación
de sesión activa. Un reset exitoso o cambio de correo revoca las sesiones previas.

## HU45: edición coherente de perfil

`PUT /auth/perfil` y `PUT /usuarios/perfil` validan el mismo `UsuarioPerfilDTO`,
utilizan el mismo servicio y obtienen identidad exclusivamente del principal.
Nombres/apellidos/correo siguen las reglas de registro. Un ID o saldo enviados
en el body no cambian propietario, puntos, nivel ni contraseña.

Cambios válidos: HTTP 200 con `UsuarioRespuestaDTO`. Duplicado: 409. Entrada
inválida: 400. Sin JWT válido se conserva el HTTP 403 del filtro existente.

Ambas rutas devuelven y exponen por CORS `X-Reauthentication-Required`:
`true` si cambió el correo (revoca sesiones y recuperaciones), `false` en otro caso.
Al recibir `true`, el frontend debe descartar su token y volver a autenticar.

Los errores de las cuatro HU usan JSON con `mensaje`; validación del controller
añade `errores` sin incluir valores rechazados. Fallos de BD/transacción: 503.
Contraseñas y tokens de entrada son de solo escritura JSON y se excluyen de `toString`.

## Persistencia y migración

`db/migration/V5__cuentas_recuperacion_segura.sql` es aditiva y transaccional:

1. Crea `recuperaciones_contrasena` con FK, hash único y periodo válido.
2. Crea índice de usuario y unicidad del correo normalizado.
3. Amplía `sesiones.token` a varchar(2048), conservando valores existentes.

El SQL contiene guardia que solo permite `huellagodb_hu29_test`. No se aplicará
en otra base sin revisión y autorización futura. No normaliza ni elimina usuarios
preexistentes. Si hay correos duplicados, el índice falla y revierte; no borra datos.
No se utilizó `ddl-auto=update/create` para migrar.

## Pruebas y ejecución segura

La suite anterior se conserva. Nuevas pruebas cubren validaciones de servicio y
controllers, SMTP ausente/fallido simulado, hashes, tokens, BCrypt, JWT, ambas rutas
de perfil, aislamiento, concurrencia, transacciones y HTTP real local.

`CuentasPostgresHttpTest` requiere `-Dhuellago.realDb=true`. Tiene URL fija de prueba,
`spring.sql.init.mode=never`, `spring.jpa.hibernate.ddl-auto=validate` y guardia
`BasePruebasGuard` que verifica configuración y `SELECT current_database()` antes
de crear el datasource Spring. Rechaza URL alternativa, JNDI y override Hikari ajeno.
El scheduler se sustituye por un mock para no cerrar retos preexistentes. El emisor
de correo también es mock. Nunca se usan destinatarios reales.

Datos propios: correos `autonomo20261025-<UUID>@example.invalid`. Las pruebas limpian
solo sus usuarios y filas relacionadas. No borran datos ajenos ni reinician secuencias.
`target/cuentas-http-evidencia.txt` registra únicamente método, ruta y estado HTTP.

Antes de cualquier ejecución real, comprobar la base explícita con psql:

```powershell
psql -X -w -h localhost -p 5432 -U postgres -d huellagodb_hu29_test -c "SELECT current_database();"
```

Con credenciales suministradas externamente y la migración revisada/aplicada:

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/huellagodb_hu29_test'
$env:SPRING_SQL_INIT_MODE='never'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO='validate'
mvn test -Dhuellago.realDb=true
```

No iniciar mediante la configuración local sin overrides: el archivo protegido
todavía contiene sus opciones anteriores de actualización e inicialización.
Para ejecución manual usar los mismos overrides y `ddl-auto=validate`; las pruebas
automatizadas ya usan servidor local en puerto aleatorio, sin tocar el servicio
que pudiera estar ejecutándose en otro puerto.
