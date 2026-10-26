# HU05 — límites de frecuencia de recuperación

Mejora puntual de seguridad de `POST /auth/recuperar-contrasena`, posterior a la
implementación de HU05/HU03/HU04/HU45. No modifica los criterios originales,
la contraseña, los JWT, los contratos de perfil ni la lógica de restablecimiento.

## Política predeterminada

| Dimensión | Regla |
| --- | --- |
| Correo normalizado | Al menos 60 segundos entre intentos admitidos |
| Correo normalizado | Máximo 3 intentos en una ventana móvil de 900 segundos |
| IP de conexión fiable | Máximo 20 intentos en una ventana móvil de 900 segundos |
| Memoria | Máximo 10 000 claves activas entre correos e IP |

Los intentos admitidos consumen cuota aunque después el correo no exista o SMTP
falle. Así no se puede abusar del proveedor o de las consultas repitiendo fallos.
Solicitudes inválidas se rechazan por validación antes de reservar cuota.

La comprobación se hace antes de buscar la cuenta, eliminar recuperaciones,
generar secretos o contactar SMTP. Como el método sigue siendo transaccional,
Spring puede abrir una transacción/conexión antes de la comprobación; no se
afirma que un rechazo esté libre de todo coste de infraestructura.

Un rechazo devuelve HTTP 429:

```http
Retry-After: 60
Content-Type: application/json
```

```json
{"mensaje":"Demasiadas solicitudes de recuperación. Intenta nuevamente más tarde"}
```

El valor de Retry-After depende del tiempo restante y se redondea hacia arriba.
No expone correo, IP, token, hash ni la dimensión que alcanzó el límite. Se expone
la cabecera mediante CORS para que el frontend pueda indicar cuándo reintentar.

Un rechazo no elimina ni invalida la recuperación previa, no genera un nuevo
token, no contacta SMTP y no amplía el periodo de bloqueo. Después del intervalo
o ventana correspondiente se admiten nuevos intentos. Una nueva solicitud
ADMITIDA mantiene la regla anterior de sustituir el enlace previo.

## Concurrencia y limpieza

`RecuperacionContrasenaServiceImpl.solicitar` utiliza `LimiteRecuperacionService`.
Su implementación reserva correo e IP dentro de un único monitor por instancia,
comprobando ambas cuotas antes de modificar cualquiera. No realiza E/S bajo ese
monitor. Esto impide que peticiones simultáneas superen el límite en el proceso.

Se guardan solamente claves SHA-256 y fechas de intentos admitidos, sin correo/IP
en texto claro. El hash es una minimización de datos, no una garantía de anonimato
frente a ataques de diccionario. No se persiste este estado ni se registra su contenido.

La limpieza elimina fechas fuera de la ventana y claves vacías:

- Al comprobar cuotas, con limpieza global periódica diferida.
- Al alcanzar la capacidad máxima.
- Mediante una tarea programada cada 60 segundos, incluso sin tráfico.

No se expulsan claves activas para admitir otras: eso permitiría eludir límites.
Si la capacidad se llena se responde 429 temporalmente para nuevas claves. Las
claves caducan normalmente y se recupera capacidad sin intervención manual.

## IP y proxies

`OrigenRecuperacion` usa `getRemoteAddr()` de la petición original del contenedor,
desenvolviendo wrappers. No lee ni confía en `X-Forwarded-For`, `Forwarded` o
`X-Real-IP`. Los encabezados del cliente no permiten cambiar la cuota de IP.

Si `server.forward-headers-strategy` está configurado como `framework` o `native`,
se omite esta dimensión porque no se asume fiable el origen reescrito. El límite
por correo sigue activo. Fuera de una petición HTTP también se aplica por correo.

Detrás de NAT o un proxy sin reescritura, la cuota corresponde a la dirección de
conexión compartida, no necesariamente a un usuario final. La configuración de
proxies, valves o filtros externos debe revisarse expresamente antes de desplegar:
no se inventa una lista de proxies de confianza. Para limitar por IP final detrás
de proxy se necesita una integración confiable o control en el borde.

## Configuración externa

No se modifica `application.properties`. Pueden usarse variables de entorno:

| Variable | Predeterminado |
| --- | ---: |
| HUELLAGO_RECOVERY_RATE_WINDOW_SECONDS | 900 |
| HUELLAGO_RECOVERY_RATE_COOLDOWN_SECONDS | 60 |
| HUELLAGO_RECOVERY_RATE_ACCOUNT_LIMIT | 3 |
| HUELLAGO_RECOVERY_RATE_IP_LIMIT | 20 |
| HUELLAGO_RECOVERY_RATE_MAX_ENTRIES | 10000 |

No hay un interruptor para deshabilitar la protección. La configuración inválida
(límites no positivos, intervalo mayor que ventana o capacidad menor que dos)
impide crear el servicio, en lugar de dejar la recuperación sin protección.

## Alcance y decisiones pendientes

- El almacenamiento es local a una instancia. Reiniciar pierde los contadores.
  En un despliegue de N instancias independientes las cuotas pueden multiplicarse
  por N. Antes de escalar horizontalmente se necesitan contadores compartidos
  (por ejemplo en una infraestructura autorizada) o límites adecuados en el borde.
- No se requirió migración ni se modificó el esquema de ninguna base.
- Se conserva HTTP 404 para correo inexistente dentro de cuota, conforme al
  criterio original. Si se supera la cuota, prevalece 429. La enumeración de
  correos sigue siendo una decisión de seguridad pendiente; esta mejora no la
  presenta como resuelta.
- Se conserva SMTP configurable, sin envío de correos reales durante las pruebas.
- Se conservan expiración de 15 minutos, un solo uso, BCrypt y revocación de sesiones.

## Verificación

Pruebas nuevas de `LimiteRecuperacionServiceTest`, `OrigenRecuperacionTest` y
`RecuperacionFrecuenciaControllerTest`: normalización, intervalo, ventana móvil,
cuota por IP, cabeceras falsificadas, wrappers, concurrencia, limpieza, memoria
acotada, Retry-After, CORS y recuperación posterior.

`RecuperacionContrasenaServiceTest` incorpora la comprobación de que un rechazo
no accede a cuentas, tokens ni correo. `CuentasPostgresHttpTest` agrega verificaciones
HTTP/BD de token previo preservado, concurrencia y recuperación tras caducidad.

Las pruebas de regresión que necesitan pedir enlaces sucesivos avanzan un reloj
de prueba; no desactivan las cuotas ni esperan minutos reales. El reloj afecta
solo al limitador, no a la expiración real de los tokens. Se conservan todos los
casos y assertions previos.

La prueba PostgreSQL mantiene URL fija autorizada, guardia current_database,
sql.init.mode=never y ddl-auto=validate. El correo y el cierre colectivo siguen
simulados. Los datos propios se limpian por identificador/correo de prueba.

Logs de esta mejora: `target/hu05-limites-pruebas.log` y
`target/hu05-limites-regresion.log`. No confundir una ejecución sin
`-Dhuellago.realDb=true` con una verificación PostgreSQL real.

### Resultado ejecutado el 25/10/2026

- Batería específica inicial: 43 pruebas aprobadas, sin fallos ni omitidas.
- Regresión final: **471 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**.
- Se conservan las 446 ejecuciones anteriores y se agregan 25 verificaciones:
  10 de límite, 5 de origen IP, 6 de integración MVC, 1 de rechazo temprano en
  recuperación y 3 de HTTP/PostgreSQL.
- El total incluye 17 pruebas PostgreSQL/HTTP y 109 peticiones HTTP locales.
- La prueba con llamadas simultáneas de recuperación verificó un HTTP 200 y
  once HTTP 429, un solo envío simulado y una única recuperación persistida.
- Antes de acceder y al finalizar, `SELECT current_database()` devolvió exactamente
  `huellagodb_hu29_test`. Las pruebas también conservan su guardia de datasource.
- Se compararon instantáneas de datos antes/después: 19 tablas y 99 filas
  preexistentes, sin diferencias; cero usuarios propios y recuperaciones restantes.
  Las secuencias pueden avanzar por las inserciones de pruebas; no se reiniciaron.
- No se accedió a la base principal, no se aplicaron migraciones y no se cambió
  ningún esquema. No se requirió respaldo para una migración, porque no hubo una.
- `application.properties` y el documento obligatorio conservan los SHA-256
  registrados antes de esta mejora. `git diff --check` sin errores.
- No se realizaron git add, commits, push ni cambios de rama.
