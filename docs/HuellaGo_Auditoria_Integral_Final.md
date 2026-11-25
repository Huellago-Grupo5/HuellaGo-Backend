# Auditoría integral de HuellaGo Backend y cumplimiento académico UPC

**Corte:** 25 de noviembre de 2026. **Rama inspeccionada:** `R-Carlos`. **Alcance observado:** repositorio backend local, sus documentos, artefactos de `target` y los seis archivos UPC localizados en `C:\Users\GaToFachero\Downloads`. Esta es una evaluación técnica orientativa; no asigna nota ni sustituye la evaluación del docente. No se inició la aplicación, se ejecutaron pruebas, se accedió a PostgreSQL ni se inspeccionaron repositorios de frontend o landing. El estado del árbol local incluye 209 cambios sin confirmar; por ello, la existencia de código local no acredita publicación en GitHub.

## Fuentes y límites de evidencia

| Fuente leída | Ubicación y referencia comprobable | Límite |
| --- | --- | --- |
| Enunciado TF ABET | `upc-pre-202602-asi705-final_project-statement-abet.docx`, apartados «Funcionalidades mínimas requeridas», «Estructura del Informe», «Ética y responsabilidad», «Internacionalización & Accesibilidad», «Tecnología» y «TF-Semana15» | El nombre del archivo dice 202602, mientras el encargo indica 202620. El propio texto usa nomenclaturas de 202610 y un ejemplo de 202401. Confirmar con el docente la versión vigente. |
| Rúbrica TF principal | `RúbricaTF_1ASI705_2026-20_TF.xlsx`, hoja `Rúbrica TF`, celdas B9:G20; en especial E9, E11, E13, E15, E17 y E19 | Es la fuente de los seis criterios y sus 20 puntos. No se adjudican puntos observados. |
| Rúbrica TP | `RúbricaTP_1ASI0705_2026-20_TP.xlsx`, hoja `TP`, E19, E23, E25, E27, E29, E31 y E33 | E27 exige AWS en nivel esperado y otras nubes en nivel intermedio para TP; TF habla de despliegue sin nombrar proveedor en esa celda. |
| Competencia final | `Logro_SI705 - Rúbrica TF - Arquitectura de Aplicaciones Web.xlsx`, hojas `Competencia Curso` D9:D12 y `Competencia Específica CAC` D9:D12 | La segunda hoja nombra **SI423**, otro curso; no se usa para alterar los criterios de 1ASI0705. |
| Competencias | `RUBRICAS DE COMPETENCIAS.xlsx`, hojas `ABET 3 CAC` y `ABET 3 EAC` D9:D12; `ICACIT G` E7:E9 | También contiene hojas de otras competencias/cursos y celdas `#REF!` en `Competencia Específica CAC` K9, K11 y K13. Requiere validación académica. |
| Plantilla de participación | `upc-pre-202601-asi705-template-performance.docx`, tabla «Participant Performance Report», columnas Estudiante, Responsabilidades, Cumplió y Puntaje | Plantilla 202601; no demuestra participación individual. |
| Criterios de las 56 HU | [HuellaGo_IHC_56_HU_Criterios_Auditoria.md](HuellaGo_IHC_56_HU_Criterios_Auditoria.md), EP01–EP06, HU01–HU56 | Es una transcripción que atribuye los criterios a `IHC FINAL.pdf`; ese PDF no se inspeccionó directamente aquí. Detecta HU01 titulada «Registro de usuario» aunque su descripción y criterios son de landing. |
| Código y evidencia local | `src/main`, `src/test`, `db/migration`, `docs`, `target/surefire-reports`, registros de pasos 121–135 y declaraciones de pruebas Postman del responsable | Una prueba unitaria, un log previo, una declaración de Postman y una prueba de despliegue son evidencias distintas. No se repitieron ensayos. |

**Convenciones de la matriz:** `B` = backend, `F` = frontend/landing, `M` = mixta. En `CA BE`, `S` indica que el criterio 1, 2 o 3 requiere alguna respuesta del backend; `N`, que pertenece a la interfaz. `Implementado` se refiere a rutas y reglas observadas en el código local; **no equivale** a aceptación integral de la HU, integración frontend ni despliegue. `Parcial` señala una dependencia funcional o de verificación concreta; `NV` significa que este repositorio no permite verificarla. Los nombres y criterios proceden del archivo de 56 HU citado arriba.

## Matriz de las 56 historias de usuario

| HU | Épica; sprint Arqui | Tipo; CA BE 1/2/3 | Estado backend y evidencia local | Pendiente para aceptación íntegra |
| --- | --- | --- | --- | --- |
| 01 Landing informativa (titulada «Registro» en fuente) | EP01; no asignada | F; N/N/N | NV; sin landing en este repositorio | Verificar contenido, mockups y CTA en landing desplegada. |
| 02 Navegar al registro desde landing | EP01; no asignada | F; N/N/N | NV | Verificar enlaces y error de navegación en landing/frontend. |
| 03 Registrar usuario | EP01; S1 | M; S/S/S | Implementado: `POST /auth/register`, `UsuarioServiceImpl`, validaciones | Probar formulario y mensajes en frontend desplegado. |
| 04 Iniciar sesión | EP01; S1 | M; S/S/S | Implementado: `POST /auth/login`, JWT y sesión | Verificar redirección onboarding/dashboard en frontend. |
| 05 Recuperar contraseña | EP01; S1 | M; S/S/S | Parcial: solicitud, token y restablecimiento; `CorreoRecuperacionSmtp` exige SMTP habilitado | Configurar correo seguro y acreditar entrega de enlace y flujo completo. |
| 06 Onboarding introductorio | EP01; no asignada | F; N/N/N | NV | Verificar guía, navegación y continuidad en frontend. |
| 07 Seleccionar objetivos iniciales | EP01; no asignada | M; S/S/S | Implementado: `GET /objetivos-ambientales`, `GET/PUT /usuarios/me/objetivos` | Verificar selección y validación visible en onboarding. |
| 08 Hábitos de transporte | EP01; S1 | M; S/S/S | Implementado: `POST /habitos/transporte`, `GET /habitos` | Verificar formulario y errores visibles. |
| 09 Hábitos de energía | EP01; S1 | M; S/S/S | Implementado: `POST /habitos/energia`, validación de kWh del hogar | Verificar unidades y explicación de alcance en UI. |
| 10 Alimentación y residuos | EP01; S1 | M; S/S/S | Implementado: `POST /habitos/alimentacion-residuos` | Verificar continuidad hacia cálculo en frontend. |
| 11 Resultado inicial de huella | EP02; S1 | M; S/S/S | Implementado: cálculo, clasificación BAJO/MEDIO/ALTO; prueba Postman reportada para usuario 70 | Verificar presentación y advertencia de alcance personal/hogar. |
| 12 Dashboard principal | EP02; S1 | M; S/S/S | Implementado: `GET /dashboard` | Verificar vista responsive y estados vacíos. |
| 13 Huella total | EP02; S1 | M; S/S/S | Implementado: `GET /huella-carbono/{usuarioId}` con propiedad | Verificar cifra, unidad y periodo en UI. |
| 14 Desglose por categorías | EP02; S1 | M; S/S/S | Implementado: `GET /huella-carbono/desglose/{usuarioId}` | Verificar gráfico y límites del perímetro mixto. |
| 15 Progreso temporal | EP02; S2 | M; S/S/S | Implementado: `GET /huella-carbono/historial` | Verificar gráfico y orden con varias fechas. |
| 16 Equivalencias | EP02; S2 | M; S/S/S | Implementado: `GET /huella-carbono/equivalencias`, factores EPA documentados; Postman reportado | Verificar explicación de que son analogías estadounidenses, no reducciones reales. |
| 17 Comparación promedio | EP02; no asignada | M; S/N/S | Implementado: `GET /huella-carbono/comparacion-promedio` | Verificar visualización y cohorte suficiente en UI. |
| 18 Recalcular tras hábitos | EP02; S2 | M; S/S/S | Implementado: `POST /huella-carbono/recalcular`, actualización de hábitos | Verificar antes/después y error visible en frontend. |
| 19 Impacto diario | EP02; S2 | M; S/S/S | Implementado: `GET /huella-carbono/diario`, promedio semanal/7; Postman reportado | Mostrar «promedio diario estimado», no medición diaria. |
| 20 Exportar reporte ambiental | EP02; no asignada | M; S/S/S | Implementado: `GET /huella-carbono/reporte` PDF | Verificar descarga, errores y archivo en frontend/despliegue. |
| 21 Retos disponibles | EP03; S2 | M; S/S/S | Implementado: `GET /retos` con clasificación | Verificar presentación diario/semanal/personalizado y vacío. |
| 22 Aceptar reto | EP03; S2 | M; S/S/S | Implementado: `POST /retos/{id}/aceptar`, control de duplicado | Verificar confirmación visible. |
| 23 Progreso de retos | EP03; S2 | M; S/S/S | Implementado: `GET /retos-activos`, `PUT /retos-activos/{id}/progreso` | Verificar barra de avance en frontend. |
| 24 Finalizar reto | EP03; S2 | M; S/S/S | Implementado: `PUT /retos-activos/{id}/completar`; valida 100% | Verificar mensaje/animación; no confundir con completar recomendación. |
| 25 Eco puntos | EP03; S2 | M; S/S/S | Implementado: recompensa transaccional y consulta de logros | Verificar mensaje de error y actualización en UI. |
| 26 Nivel | EP03; S2 | M; S/S/S | Implementado: cálculo de nivel por eco puntos | Verificar rango y progreso faltante en UI. |
| 27 Insignias | EP03; S2 | M; S/S/S | Implementado: desbloqueo por puntos y retos específicos, `GET /usuarios/me/insignias`; Postman reportado | Verificar bloqueadas y aviso visual en frontend. |
| 28 Panel de recompensas | EP03; S2 | M; S/S/S | Implementado: `GET /usuarios/me/logros`, puntos/nivel/insignias | Verificar mensaje motivacional sin recompensas. |
| 29 Retos colectivos | EP03; no asignada | M; S/S/S | Parcial en evidencia: unirse/progreso/cierre y `CierreRetoColectivoProgramado` existen | Acreditar cierre automático tras vencimiento y resultado visible con datos reales. |
| 30 Avisos de logros | EP03; no asignada | M; S/S/S | Implementado: `NotificacionServiceImpl`, listado/lectura; Postman reportado | Verificar aparición inmediata y ausencia de emergente con preferencia desactivada. |
| 31 Recomendaciones IA | EP04; no asignada | M; S/S/S | Implementado local: Ollama Qwen3, captura, catálogo, validación y escritor; generación real Postman reportada | Verificar despliegue viable y explicación al usuario del alcance orientativo. |
| 32 Lista de recomendaciones | EP04; no asignada | M; S/S/S | Implementado: `GET /usuarios/me/recomendaciones`; Postman reportó 5 pendientes | Verificar orden y vacío en frontend. |
| 33 Priorización por impacto | EP04; no asignada | M; S/S/S | Implementado para alcance aprobado: rúbrica cualitativa y escenario hipotético; Postman reportó 3,34 kg CO2e/semana | Verificar supuestos y que «NO_ESTIMABLE» no se presente como ahorro medido. |
| 34 Explicación | EP04; no asignada | M; S/S/S | Implementado: presentaciones canónicas y motivo en instantánea | Verificar texto completo y contexto en UI. |
| 35 Convertir en reto | EP04; no asignada | M; S/S/S | Implementado: `POST /usuarios/me/recomendaciones/{id}/convertir-en-reto`; aceptación HTTP/PG 131 reportada | Verificar confirmación en frontend. |
| 36 Marcar recomendación realizada | EP04; no asignada | M; S/S/S | Implementado: `PUT /usuarios/me/recomendaciones/{id}/completar`; aceptación 131 reportada | UI debe invocarlo de forma explícita; completar el reto convertido no lo ejecuta. |
| 37 Refresco dinámico | EP04; no asignada | M; S/S/S | Parcial en evidencia: listener determinista `BEFORE_COMMIT`, pruebas documentadas | Completar evidencia integral para cambio relevante y ausencia de cambio, sin afirmar Ollama automático. |
| 38 Alertas prioritarias | EP04; no asignada | M; S/S/S | Implementado: notificación de prioridad ALTA real y deduplicación | Verificar destacado y orden en dashboard/frontend; preferencia desactivada. |
| 39 Filtro por categoría | EP04; no asignada | M; S/S/S | Implementado: parámetro de categoría en `GET /usuarios/me/recomendaciones`; Postman reportado | Verificar quitar filtro en frontend. |
| 40 Historial aplicado | EP04; no asignada | M; S/S/S | Implementado: `GET /usuarios/me/recomendaciones/historial`; aceptación 131 reportada | Verificar orden de varias completadas en UI. |
| 41 Ranking comunitario | EP05; no asignada | M; S/S/S | Implementado: `GET /comunidad/ranking`; privacidad comprobada en Postman | Verificar vacío y empate visible. |
| 42 Comparar promedio comunitario | EP05; no asignada | M; S/N/S | Implementado: reutiliza comparación de huellas | Verificar gráfico, cohorte y mensaje de datos insuficientes. |
| 43 Espacio comunitario | EP05; no asignada | M; S/S/N | Parcial: publicaciones y retos colectivos tienen endpoints | Verificar portada comunitaria y estado introductorio del primer ingreso. |
| 44 Compartir logros | EP05; no asignada | M; S/S/S | Implementado: `POST/GET /comunidad/publicaciones`, propiedad/privacidad | Verificar confirmación y error visible en UI. |
| 45 Editar perfil | EP05; S1 | M; S/S/S | Implementado: `PUT /usuarios/perfil` y `/auth/perfil`, validaciones | Verificar edición y reautenticación tras cambio de correo. |
| 46 Modificar hábitos | EP05; S2 | M; S/S/S | Implementado: `GET /habitos`, `PUT /habitos/{id}` | Verificar formulario prellenado y recálculo. |
| 47 Preferencias de notificación | EP05; no asignada | M; S/S/S | Implementado: `GET/PUT /usuarios/me/preferencias/notificaciones`; Postman reportado | Verificar estado de emergentes en frontend. |
| 48 Privacidad | EP05; no asignada | M; S/S/S | Implementado: `GET/PUT /usuarios/me/preferencias/privacidad`, ranking oculta privados; Postman reportado | Verificar todas las vistas comunitarias desde otra cuenta. |
| 49 Cerrar sesión | EP05; S1 | M; S/N/S | Implementado: `/auth/logout` y `DELETE /sesiones/actual` | Verificar redirección y token invalidado tras logout. |
| 50 Regresar a landing | EP05; no asignada | F; N/N/N | NV | Verificar logo, cierre y landing desplegada. |
| 51 Registrar institución | EP06; no asignada | M; S/S/S | Implementado: `POST /instituciones`; Postman reportó institución de prueba | Verificar formulario y rol administrativo en frontend. |
| 52 Dashboard institucional | EP06; no asignada | M; S/S/S | Parcial en evidencia: `GET /instituciones/{id}/dashboard`, promedio con cohorte | Probar al menos 3 miembros con huellas compatibles y vista desplegada. |
| 53 Ranking institucional | EP06; no asignada | M; S/S/S | Parcial en evidencia: ranking individual/grupal, grupos y afiliaciones; Postman básico reportado | Probar comparaciones con varios equipos/facultades y privacidad. |
| 54 Campañas institucionales | EP06; no asignada | M; S/S/S | Parcial en evidencia: crear/listar/consultar y cierre programado | Verificar publicación a miembros, cierre real y errores de formulario. |
| 55 Reporte institucional | EP06; no asignada | M; S/S/S | Implementado: `/instituciones/{id}/reportes/desempeno-ambiental/descargar` CSV; Postman reportado | Verificar archivo descargado y métricas con cohorte suficiente. |
| 56 Reducción colectiva | EP06; no asignada | M; S/S/S | Parcial en evidencia: comparación de pares compatibles y mínimo de tres | Probar reducción, aumento, inicial cero y datos insuficientes con tres miembros reales/sintéticos. |

**Resumen de planificación original:** Sprint 1 tiene 12 HU (03, 04, 05, 08–14, 45 y 49); Sprint 2 tiene 13 (15, 16, 18, 19, 21–28 y 46); otras 31 no están asignadas en las dos tablas transcritas. Los números suman 56. En Sprint 1, recuperación por correo necesita configuración y evidencia real; en Sprint 2, las vistas y mensajes requieren frontend. Las otras 31 incluyen las HUs institucionales y de IA: su implementación local no prueba que hayan sido incorporadas al Product Backlog/Sprint Backlog del curso. No se declara un porcentaje de aceptación integral a partir del código solamente.

## CUMPLIMIENTO ACADÉMICO SEGÚN ENUNCIADO Y RÚBRICAS UPC 202620

### 1. Matriz de requisitos oficiales

| Requisito | Fuente oficial | Estado | Evidencia y acción necesaria |
| --- | --- | --- | --- |
| RESTful API propio con acceso a datos y seguridad | Enunciado «Enunciado», «Tecnología»; TF E11 | Parcial | Controladores Spring, JPA y JWT presentes; falta demostrar servicio desplegado y conectado al frontend. |
| Landing y frontend Angular adaptables | Enunciado «Enunciado», «Tecnología», TF-Semana15; TF E13 | Fuera del alcance inspeccionado | No hay repositorios de landing/frontend en este workspace. Solicitar URLs, repositorios y evidencia responsive. |
| IA con problema, modelo, datos y beneficio explícitos | Enunciado «Funcionalidades mínimas requeridas»; TF E17 | Parcial | Qwen3 local selecciona acciones elegibles a partir de hábitos/huella; documentar beneficio, límites y demostración desplegable. |
| Dos APIs de terceros disponibles en la red | TF E17 | Incumplido con evidencia actual | Solo se verificó API HTTP local de Ollama. No se identificaron dos servicios externos en red consumidos funcionalmente. |
| Swagger/OpenAPI por Sprint | Enunciado «Services Documentation Evidence for Sprint Review» y «Tecnología» | Incumplido en código; despliegue NV | No hay dependencia `springdoc`, anotaciones OpenAPI ni URL documentada; elaborar ejemplos/errores/seguridad para rutas del alcance. |
| i18n `en_US`, `es_419` y a11y/ARIA | Enunciado «Internacionalización & Accessibilidad» | Backend parcial; frontend NV | No hay bundles `messages*` ni selección de idioma; errores y catálogos en español fijo. Accesibilidad es verificable solo en landing/frontend. |
| Ética, términos y ODS con meta específica | Enunciado «Ética y responsabilidad», «Relación con los ODS» | Parcial/NV | Hay privacidad y avisos de carácter orientativo, pero no se encontró evidencia de footer de términos, política o meta ODS validada en este repositorio. |
| Informe, sprints, pruebas, despliegue, colaboración, videos | Enunciado «Estructura del Informe», «TF-Semana15»; TF E9/E15/E19 | Parcial/NV | Hay docs y pruebas backend locales; faltan artefactos completos, URLs y evidencia verificable de equipo, frontend, landing y videos. |

### 2. Seis criterios TF, máximo 20 puntos

| Criterio de `Rúbrica TF` | Máximo | Estado observado | Evidencia que falta para nivel esperado |
| --- | ---: | --- | --- |
| Product Backlog y Sprint Backlog, E9 | 3 | Parcial | La transcripción enumera 56 HU, pero 31 no aparecen asignadas a S1/S2; no se verificó board con tareas de 4–8 horas, estimaciones, estados y evolución de todos los sprints. |
| API REST desplegada, E11 | 6 | Parcial en funcionalidad local; despliegue no verificado | E11 desglosa 1 despliegue/cobertura, 1 GitHub, 2 CRUD/seguridad/reportes y 2 sustentación con respuestas y citas. Existen endpoints y repositorio local, pero no URL desplegada, no prueba del 100%, hay 209 cambios locales sin confirmar y la exposición no se evalúa aquí. |
| Frontend desplegado, E13 | 6 | Fuera del alcance inspeccionado | Repositorio, URL, Responsive Web Design, flujos, integración y capacidad de modificarlo durante la exposición. |
| Video About-the-Product, E15 | 1 | No verificado | URL y video que explique propósito, beneficio y demostración. |
| Evolution y APIs externas, E17 | 2 | Parcial | Cambios iterativos locales observables; faltan dos APIs disponibles en la red con integración funcional demostrada y evidencia de feedback atendido. |
| Comunicación, E19 | 2 | No verificable desde backend | Informe final, presentación y sustentación oral/escrita; no atribuir resultados individuales sin evidencia. |

No se suman puntos obtenidos: estos estados **no son una calificación UPC**. La condición de cobertura del 100% se compara contra el alcance acordado y validado por el docente, no contra el número bruto de métodos HTTP.

### 3. TP y discrepancias entre documentos

La hoja `TP` exige Product Backlog con ID, descripción y aceptación (E19), repositorios y contribuciones (E23), backend con CRUD/reportes/seguridad para el primer tramo (E25), despliegue **AWS** para nivel esperado (E27), configuración y estilo del curso (E29) y respuesta técnica en aproximadamente un minuto (E31). No se observó URL AWS ni artefacto de despliegue. La mención de AWS en TP no debe trasladarse sin matiz a TF E11, que exige despliegue pero no fija proveedor en esa celda. La plantilla de participación 202601, el enunciado 202602 y el archivo de competencias con hoja SI423 y referencias rotas son discrepancias de fuente; confirmar la versión de 202620 con el profesor.

### 4. CRUD adecuado al dominio

| Entidad/recurso | Crear | Consultar | Modificar/estado | Eliminar | Brecha realmente exigible |
| --- | --- | --- | --- | --- | --- |
| Usuario/cuenta | `POST /auth/register` | login/datos de respuesta y dashboard | `PUT /usuarios/perfil`, contraseña | No | No se deduce un DELETE de HU03/45; comprobar que frontend recupere datos de perfil antes de editar. |
| Sesión | login | validación JWT | logout/invalida sesión | `DELETE /sesiones/actual` | No requiere exposición de sesiones ajenas. |
| Hábitos | POST por 3 formularios | `GET /habitos` | `PUT /habitos/{id}` | No | Conservar historia de cálculo; borrado no solicitado por HU46. |
| Objetivos | catálogo existente | `GET /objetivos-ambientales`, propios | `PUT /usuarios/me/objetivos` | No | Administración del catálogo no exigida al usuario. |
| Huellas | calcular/recalcular | actual, desglose, diario, historial, promedio, PDF | nuevo cálculo, no sobrescribir histórico | No | Correcto conservar históricos; evidencia de cálculo comparable y frontend. |
| Retos/participación | campañas, conversión, aceptación | disponibles, colectivos, activos | progreso, completar, cierre | No para historial | No crear borrado genérico; falta acreditar ciclo programado de cierre. |
| Recomendaciones | generación manual/HU37 | pendientes, categoría, historial | completar, convertir | No | Historial e instantáneas deben permanecer; no se requiere DELETE. |
| Insignias/eco puntos | reglas al completar | colección y logros | acumulación controlada | No | No CRUD público de recompensas; evitar emisión arbitraria. |
| Notificaciones | eventos internos | lista/contador | leer una/todas | No | Preferencia controla nuevas; conservar históricas. |
| Publicaciones | compartir logro/reto | lista comunitaria | No observado | No observado | HU44 exige publicar/consultar, no edición ni borrado. Retención/eliminación por privacidad exige decisión adicional. |
| Instituciones/afiliaciones/grupos | registro, invitación, grupo | administración, grupos, ranking | asignar/renombrar grupos | quitar grupo/miembro | Institución no tiene edición/borrado general; HU51 no lo exige. Verificar permisos y reglas de afiliación con frontend. |
| Campañas/reportes | crear campaña | listado, detalle, dashboard, CSV | progreso y cierre | No | No borrar campañas históricas; demostrar reporte de cohorte y cierre. |

La rúbrica pide funcionalidades CRUD **del Product Backlog**, no cuatro verbos para toda tabla. La brecha verificable principal es evidencia de flujos completos y documentación, no agregar DELETE a entidades históricas.

### 5. Swagger y OpenAPI

`pom.xml` no declara `springdoc-openapi`; no se encontraron `@Operation`, `@ApiResponse`, `@Schema` ni especificación OpenAPI. Por tanto **no hay soporte OpenAPI verificable en este código**. No se arrancó el backend ni se solicitó `/swagger-ui` o `/v3/api-docs`; su disponibilidad runtime o desplegada permanece no verificada. Se encontraron aproximadamente 74 métodos HTTP en controladores (73 anotaciones habituales y un `@GetMapping` con nombre plenamente calificado); la cuenta es de código, no prueba de cobertura funcional.

Para nivel esperado hacen falta: especificación de rutas y DTO, parámetros, ejemplos request/response, errores 400/401/403/404/409/503, esquema Bearer JWT, tipos `application/pdf` y `text/csv` con `Content-Disposition`, condiciones de privacidad y URL de documentación desplegada por Sprint. Los manejadores HTTP existentes documentan comportamiento en código, no en OpenAPI. Estimación de trabajo: **varios días** para documentar y revisar todas las rutas; aumenta si los contratos cambian o faltan ejemplos reales. No se propone publicar Swagger sin protegerlo según el entorno.

### 6. APIs de terceros realmente consumidas

| Elemento | Tipo, proveedor, URL y consumidor | Autenticación/datos/evidencia | ¿Cuenta para TF E17? |
| --- | --- | --- | --- |
| Ollama Qwen3 | API HTTP **local** de Ollama, `http://localhost:11434/api/generate`; `OllamaRecomendacionesServiceImpl` vía `OllamaConfiguration` | Sin credencial en conexión local; envía contexto ambiental acotado y candidatas; recibe decisión estructurada validada. Usuario reportó Postman 200 y persistencia. No hay disponibilidad de red ni despliegue demostrados. Valor: selección personalizada explicable. | **No se contabiliza automáticamente** como API «disponible en la red»; interpretación final del docente. |
| Backend HuellaGo | API propia en `127.0.0.1:8081` | JWT; endpoints internos del producto | No es API de tercero. |
| PostgreSQL, Spring, PDFBox, Swagger | Base de datos, frameworks/bibliotecas/documentación | No son APIs de terceros consumidas en red. | No. |
| EPA | URL bibliográfica de equivalencias en `HuellaEquivalenciasDTO` | Factores incorporados al código; no hay llamada HTTP a EPA. | No. |
| SMTP | `CorreoRecuperacionSmtp` | Protocolo de correo condicionado a configuración; no se observó proveedor activo. | No se cuenta como API integrada sin servicio y evidencia concretos. |

No apareció otro cliente HTTP externo en `src/main`. Alternativas con valor de usuario, **sin implementar**: datos de calidad del aire para contextualizar decisiones de movilidad mediante [Open-Meteo Air Quality API](https://open-meteo.com/en/docs/air-quality-api), y datos geográficos de infraestructura ciclista de [OpenStreetMap Overpass API](https://wiki.openstreetmap.org/wiki/Overpass_API). Ambas requieren seleccionar una función real, condiciones de uso, atribución, límites, privacidad de ubicación y prueba de red; dos llamadas decorativas no satisfacen el propósito del criterio. También podrían evaluarse APIs ya usadas por frontend si se aporta su código y evidencia funcional.

### 7. Despliegue y configuración

`application.properties` fija `127.0.0.1:8081`, PostgreSQL `localhost`, modelo local `qwen3:4b-instruct` y `huellago.ollama.enabled=true`. Hibernate usa `ddl-auto=update` y `spring.sql.init.mode=always`; `data.sql` contiene catálogos y datos semilla. El archivo de propiedades está rastreado por Git e incluye una propiedad de contraseña de base: **retirar y rotar el secreto antes de publicar**. `JwtUtilService` permite secreto externo y, si falta, genera clave efímera que invalida tokens al reiniciar; no se observó estrategia de secretos persistentes para despliegue. Numerosos controladores usan `@CrossOrigin("*")`; debe limitarse a orígenes reales del frontend antes de publicar. `SecurityConfiguration` exige JWT salvo cuatro rutas de cuenta; no se revisó desde Internet.

No hay Dockerfile, Compose, manifiesto de nube, CI de despliegue ni URL pública en este repositorio. La carpeta `db/migration` contiene SQL histórico, pero no se encontró Flyway/Liquibase en `pom.xml`; esas migraciones no se aplican automáticamente. `CierreRetoColectivoProgramado` se ejecuta cada minuto con bandera habilitada por defecto; en múltiples instancias se debe verificar coordinación y resultados. `CorreoRecuperacionSmtp` requiere SMTP/TLS habilitado y URL de restablecimiento; sin ello HU05 no opera completamente. Ollama local deja de ser accesible al backend cuando se despliega en otra máquina; Qwen3 requiere memoria/cómputo que no se ha dimensionado ni demostrado en nube. El cliente distingue timeout, conexión y respuesta inválida; la generación IA no usa recomendación determinista como falso éxito.

Ruta de menor complejidad para una entrega desplegada: configurar un único backend Java y PostgreSQL gestionado en entorno aislado, con secretos externos, CORS limitado, `ddl-auto=validate`, inicialización controlada y documentación OpenAPI; desplegar landing/frontend por separado y verificar la integración. **Si la IA debe funcionar en la URL desplegada**, provisionar el modelo con recursos suficientes o acordar con el docente otro servicio de inferencia; apagar Ollama sin alternativa funcional no demuestra el alcance de HU31. AWS es especialmente relevante por TP E27. No se configuró ni aprovisionó infraestructura durante esta auditoría.

### 8. Internacionalización, accesibilidad, ética y sostenibilidad

No hay `MessageSource`, bundles `messages_en_US`/`messages_es_419` ni negociación de idioma para Web Services. Mensajes de validación/excepción, nombres de categorías, textos editoriales y respuestas de ejemplo están en español fijo. Los IDs, códigos de estado, `accionId`, versiones y claves canónicas deben permanecer estables; se traducen etiquetas y mensajes visibles, no identidades almacenadas. PDFs y CSV incluyen texto/formato localizados de forma fija. I18n de landing/frontend y a11y/ARIA **no se pueden evaluar** sin esos repositorios o URLs.

Privacidad tiene controles de propiedad JWT, preferencias comunitarias y ranking; IA evita presentar estimaciones hipotéticas como ahorro real y separa huella del hogar de la personal. Persisten riesgos de despliegue por secretos y CORS. El enunciado requiere términos y condiciones enlazados en el footer de landing y aplicaciones, responsabilidad ética/profesional y ODS con **meta específica**; este backend no contiene esos footers ni prueba de consentimiento, retención de datos o validación científica. No se inventa una meta ODS ni una certificación ambiental. Deben contrastarse propuesta original, informe final y pantallas reales.

### 9. IA: valor y complejidad defendible

El problema es elegir recomendaciones relacionadas con hábitos y huella vigente; Qwen3 selecciona dentro de acciones compatibles/autorizadas y puede abstenerse. **Java** normaliza hábitos, determina elegibilidad, aplica permisos editoriales, valida estructura, presenta plantillas, calcula prioridades/escenarios ambientales, revalida vigencia, aplica idempotencia y persiste. El modelo no calcula factores, cuantías de CO2e ni permisos. Los datos necesarios son hábitos del usuario autenticado, acciones realizadas y, cuando sea válida, huella orientativa; el beneficio es orientación personalizada sin fabricar ahorros.

La composición pasa por captura, catálogo, política, selector V2, presentador, coordinador, escritor y adaptador HTTP. Las validaciones, propiedad, límites transaccionales, instantáneas, idempotencia y restricciones editoriales evitan errores importantes. Como simplificación **opcional** para sustentar, documentar un diagrama de una página del recorrido y retirar solo componentes experimentales no invocados en producción tras comprobar referencias; no eliminar seguridad ni cambiar la metodología. `PUBLICABLE` sigue rechazado y el registro actual acredita **uso universitario local**, no publicación general.

### 10. Peso, empaquetado y evidencias

Medición local de solo lectura: `src` **1,89 MB / 412 archivos**, `docs` **0,17 MB / 14**, `.git` **0,91 MB / 859**, `target` **252,20 MB / 5 285**. En `target`, `maven-repository` aporta **154,74 MB**, `maven-runtime-home` **79,73 MB**, `surefire-reports` **6,36 MB**, `classes` **1,53 MB** y `test-classes` **2,24 MB**. El árbol rastreado por Git tiene 287 rutas; `target/` está ignorado. No había JAR en la raíz de `target`, por lo que **no se verificó** el contenido real del artefacto distribuible. Con el empaquetado Maven estándar, `src/main/resources` sería candidato a incluir `application.properties` y `data.sql`: confirmar y corregir el secreto antes de distribuir.

No se debe borrar `target` a ciegas: contiene logs de pruebas, exportaciones editoriales, documentos de pasos 113–135, evidencias HTTP/PG y matrices que pueden ser únicas para la entrega. Hay 139 archivos de resumen Surefire, pero corresponden a ejecuciones acumuladas y **no** prueban por sí solos una regresión final única. Los reportes Surefire, clases y cachés Maven son regenerables, pero **no** sustituyen automáticamente las evidencias de ejecución o validación. Antes de una limpieza posterior: inventariar cada evidencia única, trasladar copias sanitizadas a un lugar versionado/acordado, comprobar nombres/fechas/autoría y recién entonces decidir qué cachés y reportes repetidos pueden recrearse. Ningún archivo se eliminó o movió aquí. El tamaño del código fuente y Git es muy inferior al de `target`; el JAR distribuible debe medirse por separado cuando exista.

### 11. Preparación para sustentación

Prioridad de conceptos que cada integrante debe poder explicar con código y un ejemplo: (1) JWT, propietario, sesiones y recuperación; (2) JPA/relaciones, migraciones y transacciones; (3) cálculo de huella, factores, unidades, perímetro mixto y límites; (4) ciclo de retos, puntos, niveles, insignias y notificaciones; (5) IA: decisión de Qwen3 frente a reglas de Java, autorización local, validación y ausencia de transacción durante inferencia; (6) idempotencia/concurrencia; (7) privacidad y cohortes institucionales; (8) Swagger, despliegue y dos APIs externas si se implementan; (9) frontend, i18n/a11y y ODS/ética. Preparar respuestas con referencia a clase y fuente, sin atribuir contribución individual sin Git/board/informe respaldatorio.

Las clases más costosas de defender por tamaño y acoplamiento son `RecomendacionServiceImpl` (38 KB), `HuellaCarbonoServiceImpl` (27 KB), `PresentacionRecomendacionesIAServiceImpl` (20 KB), `PersistenciaRecomendacionesIAServiceImpl` (19 KB), `ValidadorRecomendacionFundamentadaIA` (19 KB), `EntradaRecomendacionesIA` (15 KB), además de `ReporteInstitucionalServiceImpl`. Hay mezcla de `@Autowired` en campos, composición explícita y construcciones compactas en una línea. No es obligatorio reescribirlas antes de entregar; conviene una guía de flujo, contratos y responsabilidades. El docente puede pedir cambios de frontend durante la exposición; sin su repositorio no se puede evaluar la capacidad del equipo para realizarlos. Si se simplifica código, hacerlo únicamente con pruebas de regresión y preservando la arquitectura real.

### 12. Plan priorizado, obligatorio frente a opcional

1. **Obligatorio para demostrar el TF esperado:** confirmar con el docente versión de enunciado/rúbricas y alcance final; completar backlog/sprints con tasks, horas, estados y evidencia GitHub; confirmar repositorios/URLs de landing y frontend y sus flujos.
2. **Obligatorio para API desplegada:** retirar secretos del artefacto/repositorio, configurar PostgreSQL/SMTP/JWT/CORS y esquema de despliegue, desplegar backend, probar endpoints y documentarlos con OpenAPI en URL accesible. La configuración local actual no es despliegue.
3. **Obligatorio para Evolution esperado:** demostrar dos integraciones de terceros accesibles en red que aporten valor, o obtener interpretación explícita del docente sobre Ollama local; no cuentan PostgreSQL ni citas EPA.
4. **Obligatorio para criterios originales aún sin evidencia:** cerrar HU05 correo real, HU29/HU54 cierre colectivo, HU52/HU53/HU56 con cohortes/grupos, y validar vistas/estados frontend de las 56 HU según la matriz. No repetir pruebas ya demostradas sin motivo.
5. **Obligatorio de entrega:** i18n de servicios y productos `en_US`/`es_419`, a11y/ARIA en frontend/landing, términos enlazados, ODS y meta específica sustentada, informe con evidencias por Sprint, video, presentación e informe de participación auténtico.
6. **Opcional tras lo anterior:** mejorar legibilidad de clases grandes, automatizar despliegue, depurar cachés de `target` preservando evidencia y ampliar funciones CRUD solo si un criterio concreto lo requiere.

**Conclusión:** el backend local cubre gran parte de los flujos de las 56 HU y tiene pruebas previas, pero la evidencia disponible **no acredita** 100% del alcance académico UPC: faltan despliegue, OpenAPI, dos APIs de red, frontend/landing, i18n/a11y y artefactos de sustentación. No se implementaron correcciones ni se modificaron código, datos o Git durante esta auditoría.
