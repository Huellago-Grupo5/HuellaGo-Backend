# Verificación de HU05 / HU03 / HU04 / HU45 — 25/10/2026

## Resultado

- Rama conservada: R-Carlos. Commit de partida y de cierre: d7b733d.
- Maven: BUILD SUCCESS, 446 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Se conservaron las 331 pruebas anteriores y se añadieron 115 ejecuciones.
- 14 pruebas PostgreSQL/HTTP reales, con 85 solicitudes HTTP al servidor local.
- No se realizaron commits, pushes ni cambios de rama.
- No se implementaron otras HU ni frontend.

## Protección de configuración

Los archivos protegidos conservan exactamente su SHA-256 inicial:

| Archivo | SHA-256 |
| --- | --- |
| src/main/resources/application.properties | 6CB557F51A6CF8012E7186C8923C245263F0093ABD01B9AFB29DF23082A4E438 |
| docs/HuellaGo_IHC_56_HU_Criterios_Auditoria.md | E4D2A2151E8241DC04DBC6BFB144315A2D3306BD97AC9A76CBFAF5E469FFF9D0 |

El primero ya estaba modificado y el segundo sin seguimiento antes de esta sesión.

## Base de prueba y respaldo

Todas las conexiones de esta sesión se dirigieron explícitamente a
`jdbc:postgresql://localhost:5432/huellagodb_hu29_test`. Se verificó
`SELECT current_database()` antes de migrar, al iniciar el contexto real, en cada
prueba y al terminar. No se abrió ni consultó la base principal.

Respaldo completo PostgreSQL en formato custom, fuera del repositorio:

`C:\Users\GATOFA~1\AppData\Local\Temp\HuellaGo-backup-20261025-041808\huellagodb_hu29_test.dump`

- Tamaño: 53 701 bytes.
- SHA-256: D221FBF05E21FC13C4F0D894F99D71746003FE13B42D3BF6B56E4CB38140A24E.
- pg_dump 18.6 terminó con código 0; pg_restore pudo listar el archivo y
  convertirlo completamente a SQL sin conectarse a ninguna base.
- No se ensayó una restauración en otra base, porque no estaba autorizada.
- Mantener el respaldo privado: contiene los datos existentes. Conviene conservar
  una copia segura fuera del directorio temporal si se necesita retención prolongada.

Se aplicó únicamente V5, transaccional y aditiva, después del respaldo y de verificar
el esquema y la ausencia de correos normalizados duplicados. Cambios:
tabla de recuperaciones, sus índices/restricciones, índice único de correo y
ampliación de sesiones.token. No se modificaron filas preexistentes.

Después de las pruebas se generó otro dump SQL y se compararon las secciones COPY
ordenadas de todas las tablas anteriores: 18 tablas, 99 filas, ninguna diferencia.
La única tabla nueva es public.recuperaciones_contrasena. Quedaron cero registros
de prueba y cero recuperaciones. Las secuencias pueden avanzar por INSERT y rollback;
no se reiniciaron, pues hacerlo interferiría con datos o sesiones ajenas.

Consultas realizadas: current_database, metadatos de tablas/índices, comprobación
de duplicados y datos de usuarios sintéticos. Las pruebas insertaron sus usuarios,
sesiones y recuperaciones; actualizaron exclusivamente sus propios datos para
simular expiración, perfil y reset; eliminaron únicamente sus registros al terminar.
El scheduler fue sustituido por un mock durante el contexto real.

## Pruebas nuevas

| Clase | Ejecuciones |
| --- | ---: |
| RecuperacionContrasenaServiceTest | 22 |
| CorreoRecuperacionSmtpTest | 5 |
| RegistroCuentaTest | 25 |
| AccesoCuentaServiceTest | 10 |
| PerfilCuentaValidacionTest | 15 |
| JwtCuentaTest | 4 |
| BasePruebasGuardTest | 6 |
| CuentaControllerTest | 14 |
| CuentasPostgresHttpTest | 14 |
| Total nuevas | 115 |

Las dos clases de pruebas anteriores modificadas solo reciben las nuevas
dependencias simuladas necesarias; no se eliminaron casos ni assertions.

HTTP real verificado:

| Endpoint | Estados observados |
| --- | --- |
| POST /auth/register | 201, 400, 409 |
| POST /auth/login | 200, 400, 401 |
| POST /auth/recuperar-contrasena | 200, 404, 503 |
| POST /auth/restablecer-contrasena | 204, 400 |
| PUT /auth/perfil | 200, 400, 403, 409 |
| PUT /usuarios/perfil | 200, 400, 403, 409 |
| POST /auth/logout | 200 |
| GET /usuarios/me/nivel | 200, 403 |

El emisor de correo fue simulado en todas las pruebas. No se entregaron mensajes
a destinatarios reales ni se afirma entrega SMTP real.

Los escenarios reales incluyen consumo concurrente del mismo token (uno 204 y otro
400), registro concurrente (uno 201 y otro 409), rollback de reset, rollback de
solicitud por error SMTP simulado, login concurrente con reset, duplicados de perfil,
invalidación por cambio de correo, reutilización de token, sesiones independientes,
cierre de sesión y autenticación con la clave nueva después del reset.

## Incidencias resueltas

- Maven no podía escribir en la caché protegida del usuario; se utilizó una caché
  generada dentro del workspace y finalmente se movió bajo target, sin modificar
  la caché original. La descarga necesaria se ejecutó mediante la autorización de
  Maven disponible. La ejecución final fue offline.
- Jackson podía construir DTO directamente sin pasar por setters: la normalización
  se hizo coherente también en constructores, además de validación en servicio.
- La prueba de recuperación necesitaba inyección explícita de EntityManager en el
  constructor; se corrigió sin omitir comprobaciones ni debilitar validaciones.
- Se comprobó la ampliación real de sesiones.token para soportar JWT con jti y
  correos largos. No se recurrió a actualización automática de esquema.

Los warnings de Lombok/Mockito sobre APIs futuras de Java y el SQLState 23505 del
test concurrente esperado no fueron fallos de la suite. El conflicto SQL se traduce
a HTTP 409; no se expone su detalle al cliente.

## Reproducción local

Comprobar primero current_database por conexión explícita a la base permitida.
El script siguiente utiliza la caché creada en esta sesión y no contiene secretos:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-26.0.2'
$env:MAVEN_OPTS='-Duser.home=C:\Proyectos\HuellaGo-Backend\target\maven-runtime-home'
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/huellagodb_hu29_test'
$env:SPRING_SQL_INIT_MODE='never'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO='validate'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o test '-Dhuellago.realDb=true'
```

La caché de target es descartable. Si se limpia target, usar la caché/dependencias
locales habituales y adaptar MAVEN_OPTS. El código mantiene la versión objetivo
Java 21 del pom existente, compilando y probando con JDK 26.0.2.

Evidencias generadas, ignoradas por Git:

- target/cuentas-suite-completa.log
- target/surefire-reports/
- target/cuentas-http-evidencia.txt
- target/cuentas-datos-verificacion.json

No queda un servidor HTTP de esta sesión ejecutándose: el contexto de pruebas
usa un puerto local aleatorio y se cierra al finalizar Maven.

## Pendientes de integración

- Configurar SMTP y comprobar entrega real, sin reutilizar secretos de desarrollo.
- Configurar HUELLAGO_JWT_SECRET para sesiones persistentes entre reinicios y
  despliegues con varias instancias. La clave fija anterior dejó de ser válida.
- Adaptar frontend al mensaje de recuperación sin token, al enlace recibido por
  correo, a categoriasPendientes y a X-Reauthentication-Required en ambas rutas.
- Revisar límites de frecuencia de recuperación/login en despliegue y política
  de retención de recuperaciones consumidas/expiradas.
- No reaplicar migraciones V1–V4 ni modificar el archivo local protegido para
  ejecutar estas pruebas. V5 ya quedó aplicada en la base autorizada.
