# MATRIZ DE REFERENCIA – AUDITORÍA INTEGRAL DEL BACKEND HUELLAGO

> **Documento de lectura para Codex, no una certificación de implementación.** Se extrajeron los textos de las 56 historias y sus 168 criterios de aceptación de **IHC FINAL.pdf** (páginas físicas 87–105); se preservan sus denominaciones originales, incluso inconsistencias de título. El **AVANCE ARQUI WEB.pdf** determina el enfoque de trabajo actual y documenta las 25 HU de los dos Sprint Backlogs (páginas físicas 63–65 y 82–84).

## Fuentes y reglas de interpretación

- **Requisitos de origen:** IHC FINAL.pdf, sección 3.2, User Stories, pp. 85–105. Son 6 épicas, 56 HU (HU01–HU56), con tres criterios de aceptación por HU.
- **Planificación académica actual:** AVANCE ARQUI WEB.pdf, Sprint Backlog 1 y Sprint Backlog 2. NO usar la distribución de sprints de la landing page del curso IHC como sustituto de la distribución de Arquitectura Web.
- **Límite de esta auditoría:** Solo el backend Spring Boot (API REST, persistencia, reglas de negocio, seguridad, servicios, consultas, reportes, integración técnica); el frontend y su navegación, diseño, componentes visuales, formularios, gráficos y mensajes en pantalla corresponden a otra persona. No marcar una HU como incumplida porque falten pantallas si el contrato backend requerido existe; sí evidenciar todo backend necesario que falte.
- **Estado «Completado» en un documento ≠ evidencia de backend correcto:** Codex debe validar implementación real y criterios, no confiar en etiquetas documentales o nombres de métodos.
- **Sin acceso a BD en la auditoría:** inspección de archivos y tests existentes, sin ejecutar DB/migraciones ni modificaciones.
- **Inconsistencia original:** HU01 tiene título «Registro de usuario», pero su descripción y criterios tratan de landing page; preservar tal cual y auditar por contenido, no corregir el original silenciosamente.

## Épicas de IHC FINAL (página 86)

- **EP01:** Acceso, Onboarding y Configuración Inicial.
- **EP02:** Medición y Dashboard de Huella de Carbono.
- **EP03:** Retos Ecológicos, Gamificación y Recompensas.
- **EP04:** Recomendaciones Inteligentes y Acciones Personalizadas.
- **EP05:** Comunidad, Comparación Social y Configuración Final.
- **EP06:** Módulo Institucional y Monitoreo Empresarial.

## Sprint Backlogs según AVANCE ARQUI WEB

**Sprint 1 (12 HU):** HU03, HU04, HU05, HU08, HU09, HU10, HU11, HU12, HU13, HU14, HU45, HU49.

**Sprint 2 (13 HU):** HU15, HU16, HU18, HU19, HU21, HU22, HU23, HU24, HU25, HU26, HU27, HU28, HU46.

**Otras HU del Product Backlog de IHC/Arqui sin asignación en esas dos tablas (31 HU):** HU01, HU02, HU06, HU07, HU17, HU20, HU29, HU30, HU31, HU32, HU33, HU34, HU35, HU36, HU37, HU38, HU39, HU40, HU41, HU42, HU43, HU44, HU47, HU48, HU50, HU51, HU52, HU53, HU54, HU55, HU56. No asumir que están descartadas ni que fueron reasignadas a alguno de los dos sprints; reportar por separado y verificar alcance vigente.

**HU sobre las que se reportó trabajo/commits previos en la rama R-Carlos (16, evidencia histórica por corroborar en Git):** HU07, HU17, HU20, HU31, HU45, HU47, HU48, HU49, HU51, HU29, HU35, HU36, HU37, HU44, HU54, HU55.

**Advertencia especial HU55:** IHC FINAL exige en su segundo criterio que la exportación contenga «huella promedio, participación y resultados de campañas». Durante las pruebas manuales previas del reporte JSON/CSV, no se observó un campo explícito de huella promedio; es un **posible hueco**, no una conclusión definitiva. Revisar código, DTO, CSV y criterios antes de certificar cumplimiento.

## Historias y criterios de aceptación completos (fuente IHC FINAL)

### EP01 — Acceso, Onboarding y Configuración Inicial

#### HU01 — Registro de usuario
**Épica:** EP01  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 87

**Descripción original:** Como usuario interesado en sostenibilidad, quiero visualizar una landing page informativa de HuellaGo, para conocer los beneficios y funcionalidades principales antes de registrarme.

**Criterios de aceptación originales:**

1. Dado que el usuario ingresa al sitio web de HuellaGo, cuando cargue la página principal, entonces el sistema debe mostrar información general sobre la aplicación.
2. Dado que el usuario visualiza la landing page, cuando navegue por sus secciones, entonces debe poder observar beneficios, funcionalidades y mockups del producto.
3. Dado que el usuario se interese en utilizar la aplicación, cuando seleccione “Comenzar ahora”, entonces debe ser redirigido al registro.

#### HU02 — Navegación hacia registró desde landing
**Épica:** EP01  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 87

**Descripción original:** Como visitante, quiero acceder al formulario de registro desde la landing page, para comenzar a utilizar la aplicación fácilmente.

**Criterios de aceptación originales:**

1. Dado que el usuario se encuentra en la landing page, cuando presione el botón “Registrarse”, entonces el sistema debe mostrar el formulario de creación de cuenta.
2. Dado que el usuario visualiza el menú superior, cuando seleccione la opción “Crear cuenta”, entonces debe redirigirse correctamente.
3. Dado que ocurra un error de navegación, cuando el usuario intente acceder al registro, entonces el sistema debe mostrar un mensaje informativo. 88

#### HU03 — Registro de usuario
**Épica:** EP01  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 88

**Descripción original:** Como usuario nuevo, quiero crear una cuenta en HuellaGo ingresando mis datos personales, para acceder a las funcionalidades de la aplicación.

**Criterios de aceptación originales:**

1. Dado que el usuario completa el formulario de registro, cuando presione “Crear cuenta”, entonces el sistema debe almacenar la información y permitir el acceso.
2. Dado que existan campos vacíos o datos inválidos, cuando intente registrarse, entonces el sistema debe mostrar mensajes de validación.
3. Dado que el correo ya se encuentre registrado, cuando el usuario intente crear una cuenta, entonces el sistema debe impedir el registro y notificarlo.

#### HU04 — Inicio de sesión
**Épica:** EP01  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 88

**Descripción original:** Como usuario registrado, quiero iniciar sesión con mis credenciales, para ingresar a mi cuenta y continuar mi seguimiento ambiental.

**Criterios de aceptación originales:**

1. Dado que el usuario ingrese su correo y contraseña válidos, cuando presione “Iniciar sesión”, entonces el sistema debe redirigirlo al onboarding o dashboard según corresponda.
2. Dado que el usuario ingrese credenciales incorrectas, cuando intente acceder, entonces el sistema debe mostrar un mensaje de error.
3. Dado que el usuario no complete los campos obligatorios, cuando seleccione iniciar sesión, entonces el sistema debe solicitar la información faltante.

#### HU05 — Recuperación de contraseña
**Épica:** EP01  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 88

**Descripción original:** Como usuario, quiero recuperar mi contraseña mediante mi correo electrónico, para volver a acceder a mi cuenta en caso de olvido.

**Criterios de aceptación originales:**

1. Dado que el usuario seleccione “Olvidé mi contraseña”, cuando ingrese su correo registrado, entonces el sistema debe enviar un enlace de recuperación.
2. Dado que el correo ingresado no exista, cuando solicite recuperación, entonces el sistema debe mostrar un mensaje informativo.
3. Dado que el usuario acceda al enlace enviado, cuando registre una nueva contraseña válida, entonces el sistema debe actualizarla correctamente. 89

#### HU06 — Visualización de onboarding introductorio
**Épica:** EP01  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 89

**Descripción original:** Como usuario nuevo, quiero visualizar una guía introductoria sobre el funcionamiento de HuellaGo, para comprender cómo la aplicación me ayudará a reducir mi huella de carbono.

**Criterios de aceptación originales:**

1. Dado que el usuario ingrese por primera vez a la aplicación, cuando acceda tras registrarse, entonces el sistema debe mostrar el onboarding introductorio.
2. Dado que el usuario navegue por el onboarding, cuando presione “Siguiente”, entonces debe visualizar la siguiente explicación.
3. Dado que el usuario finalice el onboarding, cuando seleccione “Continuar”, entonces debe ser redirigido a la configuración inicial.

#### HU07 — Selección de objetivos ecológicos iniciales
**Épica:** EP01  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 89

**Descripción original:** Como usuario nuevo, quiero seleccionar mis principales objetivos ambientales dentro del onboarding, para recibir una experiencia inicial más personalizada.

**Criterios de aceptación originales:**

1. Dado que el usuario se encuentre en la etapa de personalización inicial, cuando visualice las opciones, entonces debe poder seleccionar uno o más objetivos ecológicos.
2. Dado que el usuario confirme sus objetivos, cuando presione “Guardar”, entonces el sistema debe registrar la selección.
3. Dado que el usuario no seleccione ningún objetivo, cuando intente continuar, entonces el sistema debe solicitar al menos una opción.

#### HU08 — Registro de hábitos de transporte
**Épica:** EP01  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 89

**Descripción original:** Como usuario, quiero ingresar información sobre mis hábitos de transporte, para que el sistema considere este factor en el cálculo de mi huella de carbono.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda al formulario de hábitos, cuando complete sus datos de transporte, entonces el sistema debe guardarlos correctamente.
2. Dado que el usuario seleccione medios de transporte y frecuencia de uso, cuando continúe, entonces la información debe añadirse al perfil ambiental.
3. Dado que el usuario deje campos incompletos, cuando intente avanzar, entonces el sistema debe solicitar completar la información. 90

#### HU09 — Registro de hábitos de consumo energético
**Épica:** EP01  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 90

**Descripción original:** Como usuario, quiero ingresar información sobre mi consumo energético diario, para obtener una medición ambiental más precisa.

**Criterios de aceptación originales:**

1. Dado que el usuario se encuentre en la sección de energía, cuando registre su consumo doméstico, entonces el sistema debe almacenar los datos.
2. Dado que el usuario complete satisfactoriamente los campos, cuando presione “Continuar”, entonces debe pasar a la siguiente categoría.
3. Dado que los datos sean inválidos, cuando intente guardarlos, entonces el sistema debe mostrar un mensaje de validación.

#### HU10 — Registro de hábitos de alimentación y residuos
**Épica:** EP01  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 90

**Descripción original:** Como usuario, quiero ingresar datos sobre mi alimentación y generación de residuos, para completar la configuración inicial de mi perfil ambiental.

**Criterios de aceptación originales:**

1. Dado que el usuario complete la sección de alimentación y residuos, cuando guarde la información, entonces el sistema debe finalizar la configuración inicial.
2. Dado que el usuario termine de registrar todos sus hábitos, cuando seleccione “Calcular huella”, entonces debe ser redirigido al resultado inicial.
3. Dado que existan datos faltantes, cuando intente finalizar, entonces el sistema debe solicitar completar el formulario.

### EP02 — Medición y Dashboard de Huella de Carbono

#### HU11 — Generación de resultado inicial de huella de carbono
**Épica:** EP02  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 90

**Descripción original:** Como usuario, quiero obtener una medición inicial de mi huella de carbono al finalizar el registro de hábitos, para conocer mi nivel de impacto ambiental.

**Criterios de aceptación originales:**

1. Dado que el usuario complete la configuración inicial de hábitos, cuando seleccione “Calcular huella”, entonces el sistema debe generar un resultado total estimado de CO₂.
2. Dado que el sistema procese la información ingresada, cuando finalice el cálculo, entonces debe mostrar el nivel de impacto correspondiente.
3. Dado que exista información insuficiente, cuando se intente calcular la huella, entonces el sistema debe solicitar completar los datos faltantes. 91

#### HU12 — Visualización de dashboard principal
**Épica:** EP02  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 91

**Descripción original:** Como usuario, quiero acceder a un dashboard principal con el resumen de mi desempeño ambiental, para consultar rápidamente mi situación actual dentro de HuellaGo.

**Criterios de aceptación originales:**

1. Dado que el usuario finalice el cálculo inicial, cuando ingrese al panel principal, entonces el sistema debe mostrar el dashboard con la información resumida.
2. Dado que el dashboard cargue correctamente, cuando el usuario visualice la pantalla, entonces debe observar accesos a huella, recomendaciones, retos y comunidad.
3. Dado que ocurra un error de carga, cuando el usuario acceda al dashboard, entonces el sistema debe mostrar un mensaje informativo.

#### HU13 — Visualización de huella de carbono total
**Épica:** EP02  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 91

**Descripción original:** Como usuario, quiero visualizar mi huella de carbono total dentro del dashboard, para comprender mi impacto ambiental general.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda al dashboard, cuando cargue la sección principal, entonces el sistema debe mostrar el valor total de su huella de carbono.
2. Dado que existan datos registrados suficientes, cuando se visualice la huella, entonces debe mostrarse en unidades claras de CO₂.
3. Dado que no existan datos completos, cuando el usuario consulte la huella, entonces el sistema debe mostrar una advertencia.

#### HU14 — Desglose de huella por categorías
**Épica:** EP02  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 91

**Descripción original:** Como usuario, quiero visualizar qué actividades generan mayor impacto dentro de mi huella de carbono, para identificar en qué aspectos debo mejorar.

**Criterios de aceptación originales:**

1. Dado que el usuario consulte el análisis detallado, cuando visualice la información, entonces el sistema debe mostrar categorías como transporte, energía, alimentación y residuos.
2. Dado que existan registros válidos, cuando se cargue el desglose, entonces debe representarse mediante gráficos comprensibles. 92
3. Dado que alguna categoría no tenga información, cuando se visualice el análisis, entonces debe indicarse como incompleta.

#### HU15 — Visualización de progreso temporal
**Épica:** EP02  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 92

**Descripción original:** Como usuario, quiero observar la evolución de mi huella de carbono a lo largo del tiempo, para medir si estoy mejorando mis hábitos.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda al historial de progreso, cuando seleccione la sección temporal, entonces el sistema debe mostrar una gráfica evolutiva.
2. Dado que existan registros en diferentes fechas, cuando se visualice el historial, entonces debe reflejar cambios de aumento o reducción.
3. Dado que no existan suficientes registros, cuando se intente consultar la evolución, entonces el sistema debe mostrar un mensaje informativo.

#### HU16 — Visualización de equivalencias ecológicas
**Épica:** EP02  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 92

**Descripción original:** Como usuario, quiero observar equivalencias visuales de mi huella de carbono, para comprender de forma sencilla el impacto que genero.

**Criterios de aceptación originales:**

1. Dado que el usuario visualice su huella total, cuando acceda al detalle, entonces el sistema debe mostrar equivalencias ambientales comprensibles.
2. Dado que el sistema actualice la huella, cuando cambien los valores, entonces las equivalencias deben ajustarse automáticamente.
3. Dado que el usuario consulte una equivalencia, cuando seleccione el elemento, entonces debe mostrarse una breve explicación.

#### HU17 — Comparación con promedio de usuarios
**Épica:** EP02  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 92

**Descripción original:** Como usuario, quiero comparar mi desempeño ambiental con el promedio general de usuarios, para tener una referencia de mi nivel de impacto.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda a la sección comparativa, cuando visualice la información, entonces el sistema debe mostrar su posición frente al promedio.
2. Dado que existan datos globales registrados, cuando se genere la comparación, entonces debe representarse de forma clara.
3. Dado que no haya suficientes datos comparativos, cuando se consulte esta opción, entonces el sistema debe mostrar un mensaje informativo. 93

#### HU18 — Recalcular huella de carbono tras modificar hábitos
**Épica:** EP02  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 93

**Descripción original:** Como usuario, quiero recalcular mi huella de carbono cuando actualice mis hábitos, para visualizar cambios en mi impacto ambiental.

**Criterios de aceptación originales:**

1. Dado que el usuario modifique información de sus hábitos, cuando seleccione “Recalcular huella”, entonces el sistema debe actualizar el resultado.
2. Dado que existan nuevos datos válidos, cuando finalice el cálculo, entonces el dashboard debe reflejar los cambios.
3. Dado que ocurra un error en el procesamiento, cuando se intente recalcular, entonces el sistema debe mostrar una alerta.

#### HU19 — Visualización de impacto diario
**Épica:** EP02  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 93

**Descripción original:** Como usuario, quiero consultar mi impacto ambiental diario, para llevar un control más detallado de mis acciones cotidianas.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda a la sección de detalle diario, cuando seleccione una fecha, entonces el sistema debe mostrar el impacto correspondiente.
2. Dado que existan registros diarios, cuando se visualicen, entonces deben presentarse ordenadamente.
3. Dado que no exista información del día consultado, cuando se acceda, entonces el sistema debe indicar ausencia de datos.

#### HU20 — Exportación de reporte ambiental
**Épica:** EP02  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 93

**Descripción original:** Como usuario, quiero exportar un reporte resumido de mi huella de carbono, para compartir o revisar externamente mi progreso.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda al dashboard, cuando seleccione “Exportar reporte”, entonces el sistema debe generar un archivo descargable.
2. Dado que el archivo se genere correctamente, cuando finalice el proceso, entonces debe incluir resumen de huella y progreso.
3. Dado que ocurra un error durante la exportación, cuando el usuario intente descargarlo, entonces el sistema debe mostrar una notificación. 94

### EP03 — Retos Ecológicos, Gamificación y Recompensas

#### HU21 — Visualización de retos ecológicos disponibles
**Épica:** EP03  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 94

**Descripción original:** Como usuario, quiero visualizar una lista de retos ecológicos disponibles, para elegir acciones sostenibles que pueda realizar dentro de mi rutina.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda a la sección de retos, cuando cargue la pantalla, entonces el sistema debe mostrar retos diarios, semanales y personalizados.
2. Dado que existan diferentes tipos de retos, cuando se visualicen, entonces deben estar clasificados correctamente.
3. Dado que no haya retos activos disponibles, cuando el usuario ingrese, entonces el sistema debe mostrar un mensaje informativo.

#### HU22 — Aceptación de reto ecológico
**Épica:** EP03  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 94

**Descripción original:** Como usuario, quiero aceptar un reto ecológico dentro de la aplicación, para comprometerme a cumplir una acción sostenible.

**Criterios de aceptación originales:**

1. Dado que el usuario visualice un reto disponible, cuando seleccione “Aceptar reto”, entonces el sistema debe registrarlo como activo.
2. Dado que el reto ya se encuentre aceptado, cuando el usuario intente activarlo nuevamente, entonces el sistema debe impedir duplicados.
3. Dado que el usuario acepte correctamente el reto, cuando se procese la acción, entonces debe mostrarse una confirmación visual.

#### HU23 — Seguimiento de progreso en retos activos
**Épica:** EP03  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 94

**Descripción original:** Como usuario, quiero visualizar el progreso de mis retos activos, para saber cuánto me falta para completarlos.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda a sus retos activos, cuando consulte uno de ellos, entonces el sistema debe mostrar una barra o porcentaje de avance.
2. Dado que el usuario registre cumplimiento parcial, cuando se actualicen los datos, entonces el progreso debe reflejarse automáticamente.
3. Dado que el reto no tenga avances aún, cuando se visualice, entonces debe mostrarse en 0%. 95

#### HU24 — Finalización de reto ecológico
**Épica:** EP03  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 95

**Descripción original:** Como usuario, quiero completar un reto ecológico cumplido, para obtener reconocimiento dentro de la aplicación.

**Criterios de aceptación originales:**

1. Dado que el usuario cumpla las condiciones del reto, cuando seleccione “Finalizar reto”, entonces el sistema debe marcarlo como completado.
2. Dado que el reto no cumpla aún con los requisitos, cuando el usuario intente finalizarlo, entonces el sistema debe impedir la acción.
3. Dado que el reto sea completado correctamente, cuando finalice el proceso, entonces debe mostrarse una animación o mensaje de logro.

#### HU25 — Acumulación de eco puntos
**Épica:** EP03  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 95

**Descripción original:** Como usuario, quiero recibir eco puntos al completar retos y acciones sostenibles, para medir mi avance dentro de HuellaGo.

**Criterios de aceptación originales:**

1. Dado que el usuario complete un reto, cuando se valide la acción, entonces el sistema debe asignar eco puntos automáticamente.
2. Dado que el usuario acumule puntos, cuando acceda a su perfil de logros, entonces debe visualizar el total obtenido.
3. Dado que ocurra un error en la asignación, cuando finalice el reto, entonces el sistema debe notificarlo.

#### HU26 — Incremento de nivel ambiental
**Épica:** EP03  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 95

**Descripción original:** Como usuario, quiero subir de nivel según mis eco puntos acumulados, para sentir progreso constante dentro de la aplicación.

**Criterios de aceptación originales:**

1. Dado que el usuario alcance el puntaje requerido, cuando el sistema procese sus eco puntos, entonces debe subirlo automáticamente de nivel.
2. Dado que el usuario consulte su perfil de progreso, cuando acceda a la sección de nivel, entonces debe visualizar su rango actual.
3. Dado que el usuario aún no alcance el mínimo, cuando consulte el sistema de niveles, entonces debe mostrarse el progreso faltante. 96

#### HU27 — Obtención de insignias ecológicas
**Épica:** EP03  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 96

**Descripción original:** Como usuario, quiero desbloquear insignias por mis logros ambientales, para recibir reconocimiento visual por mis acciones sostenibles.

**Criterios de aceptación originales:**

1. Dado que el usuario complete retos específicos, cuando cumpla condiciones especiales, entonces el sistema debe otorgar una insignia.
2. Dado que el usuario acceda a su colección de logros, cuando visualice la sección de insignias, entonces debe observar las obtenidas y las bloqueadas.
3. Dado que una insignia sea desbloqueada, cuando ocurra el evento, entonces el sistema debe mostrar una notificación visual.

#### HU28 — Visualización de panel de recompensas
**Épica:** EP03  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 96

**Descripción original:** Como usuario, quiero visualizar un panel con mis puntos, nivel e insignias, para consultar de manera centralizada mis logros obtenidos.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda al módulo de recompensas, cuando cargue la pantalla, entonces el sistema debe mostrar eco puntos, nivel e insignias.
2. Dado que existan logros obtenidos, cuando se visualice el panel, entonces la información debe mostrarse actualizada.
3. Dado que el usuario aún no tenga recompensas, cuando acceda al panel, entonces debe mostrarse un mensaje motivacional.

#### HU29 — Participación en retos colectivos
**Épica:** EP03  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 96

**Descripción original:** Como usuario, quiero unirme a retos grupales dentro de HuellaGo, para contribuir junto con otros usuarios a un impacto ambiental colectivo.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda a retos colectivos, cuando seleccione uno disponible, entonces el sistema debe permitirle unirse.
2. Dado que el usuario participe en el reto grupal, cuando complete avances, entonces debe reflejarse en el progreso comunitario.
3. Dado que el reto colectivo finalice, cuando se cierre el periodo, entonces el sistema debe mostrar el resultado grupal. 97

#### HU30 — Notificación de logros obtenidos
**Épica:** EP03  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 97

**Descripción original:** Como usuario, quiero recibir notificaciones visuales cuando consiga puntos, niveles o insignias, para mantenerme motivado dentro de la aplicación.

**Criterios de aceptación originales:**

1. Dado que el usuario complete una acción relevante, cuando obtenga una recompensa, entonces el sistema debe mostrar una notificación inmediata.
2. Dado que el usuario suba de nivel o desbloquee insignias, cuando ocurra el evento, entonces debe visualizar un mensaje destacado.
3. Dado que el usuario desactive alertas internas, cuando se generen logros, entonces el sistema no debe mostrar notificaciones emergentes.

### EP04 — Recomendaciones Inteligentes y Acciones Personalizadas

#### HU31 — Generación de recomendaciones inteligentes
**Épica:** EP04  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 97

**Descripción original:** Como usuario, quiero recibir recomendaciones ecológicas personalizadas según mis hábitos registrados, para reducir mi huella de carbono de manera práctica.

**Criterios de aceptación originales:**

1. Dado que el usuario cuente con hábitos ambientales registrados, cuando el sistema procese la información, entonces debe generar recomendaciones personalizadas.
2. Dado que existen datos suficientes del usuario, cuando se calcule su perfil ambiental, entonces las sugerencias deben estar relacionadas con sus principales focos de impacto.
3. Dado que no existan datos completos, cuando se intente generar recomendaciones, entonces el sistema debe solicitar completar información.

#### HU32 — Visualización de lista de recomendaciones
**Épica:** EP04  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 97

**Descripción original:** Como usuario, quiero visualizar una lista ordenada de recomendaciones sostenibles, para identificar fácilmente acciones de mejora.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda al módulo de recomendaciones, cuando cargue la pantalla, entonces el sistema debe mostrar una lista de sugerencias disponibles.
2. Dado que existen múltiples recomendaciones, cuando se visualicen, entonces deben mostrarse de manera clara y ordenada.
3. Dado que no existan sugerencias generadas, cuando el usuario consulte esta sección, entonces debe visualizar un mensaje informativo. 98

#### HU33 — Priorización de recomendaciones por impacto
**Épica:** EP04  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 98

**Descripción original:** Como usuario, quiero que las recomendaciones se ordenen según el impacto positivo que pueden generar, para enfocarme primero en las acciones más útiles.

**Criterios de aceptación originales:**

1. Dado que el sistema muestre recomendaciones, cuando el usuario acceda a la lista, entonces las sugerencias deben ordenarse por nivel de impacto esperado.
2. Dado que existan recomendaciones de bajo y alto beneficio, cuando se cargue la información, entonces debe diferenciarse visualmente la prioridad.
3. Dado que el usuario consulte el detalle, cuando seleccione una recomendación, entonces debe visualizar el impacto estimado.

#### HU34 — Visualización de explicación de recomendación
**Épica:** EP04  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 98

**Descripción original:** Como usuario, quiero comprender por qué HuellaGo me sugiere una determinada acción, para confiar en la utilidad de la recomendación.

**Criterios de aceptación originales:**

1. Dado que el usuario seleccione una recomendación, cuando abra su detalle, entonces el sistema debe mostrar una breve explicación del motivo.
2. Dado que la recomendación esté relacionada con una categoría específica, cuando se visualice, entonces debe indicarse el área de impacto.
3. Dado que el usuario consulte la explicación, cuando cargue la información, entonces debe ser clara y comprensible.

#### HU35 — Conversión de recomendación en reto
**Épica:** EP04  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 98

**Descripción original:** Como usuario, quiero convertir una recomendación ecológica en un reto personal, para comprometerme activamente con esa mejora.

**Criterios de aceptación originales:**

1. Dado que el usuario visualice una recomendación, cuando seleccione “Convertir en reto”, entonces el sistema debe trasladarla a retos activos.
2. Dado que la recomendación ya haya sido activada previamente, cuando se intente convertir nuevamente, entonces el sistema debe impedir duplicados.
3. Dado que la acción se procese correctamente, cuando finalice, entonces debe mostrarse una confirmación visual. 99

#### HU36 — Registro de recomendaciones completadas
**Épica:** EP04  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 99

**Descripción original:** Como usuario, quiero marcar recomendaciones como realizadas, para llevar un control de las acciones sostenibles que ya implementé.

**Criterios de aceptación originales:**

1. Dado que el usuario complete una recomendación sugerida, cuando seleccione “Marcar como realizada”, entonces el sistema debe registrarla como completada.
2. Dado que la recomendación haya sido completada, cuando el usuario consulte su historial, entonces debe mostrarse en la lista de acciones realizadas.
3. Dado que ocurra un error de actualización, cuando se marque la recomendación, entonces el sistema debe mostrar una alerta.

#### HU37 — Actualización dinámica de recomendaciones
**Épica:** EP04  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 99

**Descripción original:** Como usuario, quiero que las recomendaciones cambien según la evolución de mis hábitos, para recibir sugerencias siempre relevantes.

**Criterios de aceptación originales:**

1. Dado que el usuario actualice sus hábitos o complete retos, cuando el sistema procese nueva información, entonces debe refrescar las recomendaciones.
2. Dado que existan cambios significativos en la huella de carbono, cuando se genere una nueva evaluación, entonces deben mostrarse nuevas sugerencias.
3. Dado que no existan variaciones relevantes, cuando se actualice el perfil, entonces el sistema puede mantener recomendaciones previas.

#### HU38 — Notificaciones de recomendaciones prioritarias
**Épica:** EP04  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 99

**Descripción original:** Como usuario, quiero recibir alertas sobre recomendaciones de alto impacto, para no dejar pasar acciones importantes de mejora.

**Criterios de aceptación originales:**

1. Dado que el sistema detecte una recomendación prioritaria, cuando el usuario ingrese a la app, entonces debe visualizar una alerta destacada.
2. Dado que exista una sugerencia urgente de mejora, cuando cargue el dashboard, entonces esta debe mostrarse primero.
3. Dado que el usuario desactive notificaciones internas, cuando se generen alertas prioritarias, entonces no deben mostrarse emergentes. 100

#### HU39 — Filtro de recomendaciones por categoría
**Épica:** EP04  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 100

**Descripción original:** Como usuario, quiero filtrar recomendaciones según el área ambiental que deseo mejorar, para enfocarme en acciones específicas.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda al módulo de recomendaciones, cuando seleccione una categoría, entonces el sistema debe mostrar solo sugerencias relacionadas.
2. Dado que existan categorías como transporte, energía o alimentación, cuando el usuario filtre, entonces deben actualizarse correctamente.
3. Dado que el usuario quite el filtro, cuando vuelva a la vista general, entonces deben mostrarse todas las recomendaciones.

#### HU40 — Historial de recomendaciones aplicadas
**Épica:** EP04  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 100

**Descripción original:** Como usuario, quiero revisar un historial de recomendaciones que ya implementé, para visualizar mis mejoras sostenibles acumuladas.

**Criterios de aceptación originales:**

1. Dado que el usuario haya completado recomendaciones, cuando acceda al historial, entonces el sistema debe mostrar el listado de acciones aplicadas.
2. Dado que existan varias recomendaciones realizadas, cuando se visualicen, entonces deben aparecer ordenadas cronológicamente.
3. Dado que aún no existan recomendaciones completadas, cuando el usuario consulte esta sección, entonces debe mostrarse un mensaje informativo.

### EP05 — Comunidad, Comparación Social y Configuración Final

#### HU41 — Visualización de ranking de usuarios
**Épica:** EP05  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 100

**Descripción original:** Como usuario, quiero visualizar un ranking general de desempeño ambiental, para comparar mi progreso con otros miembros de la comunidad HuellaGo.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda al módulo de comunidad, cuando cargue la pantalla, entonces el sistema debe mostrar un ranking de usuarios.
2. Dado que existan puntajes registrados, cuando se visualice el ranking, entonces deben mostrarse ordenados de mayor a menor.
3. Dado que no existan suficientes datos comunitarios, cuando el usuario consulte esta opción, entonces debe mostrarse un mensaje informativo. 101

#### HU42 — Comparación con promedio comunitario
**Épica:** EP05  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 101

**Descripción original:** Como usuario, quiero comparar mi huella de carbono con el promedio de la comunidad, para medir mi nivel de sostenibilidad frente a otros.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda a la sección comparativa, cuando visualice el análisis, entonces el sistema debe mostrar su huella frente al promedio general.
2. Dado que existan datos comunitarios disponibles, cuando se procese la comparación, entonces debe representarse gráficamente.
3. Dado que no existan registros suficientes, cuando se intente consultar la comparación, entonces el sistema debe indicarlo.

#### HU43 — Participación en comunidad ecológica
**Épica:** EP05  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 101

**Descripción original:** Como usuario, quiero acceder a un espacio comunitario dentro de HuellaGo, para sentirme parte de un impacto ambiental colectivo.

**Criterios de aceptación originales:**

1. Dado que el usuario ingrese al módulo de comunidad, cuando cargue la pantalla, entonces el sistema debe mostrar actividades o interacciones comunitarias.
2. Dado que existan retos o avances grupales, cuando se visualicen, entonces deben mostrarse actualizados.
3. Dado que el usuario no tenga interacción previa, cuando ingrese por primera vez, entonces debe mostrarse contenido introductorio.

#### HU44 — Compartir logros ambientales
**Épica:** EP05  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 101

**Descripción original:** Como usuario, quiero compartir mis logros y avances ambientales dentro de la comunidad, para motivar a otros usuarios y mostrar mi progreso.

**Criterios de aceptación originales:**

1. Dado que el usuario obtenga un logro o complete un reto, cuando seleccione “Compartir”, entonces el sistema debe publicarlo en el espacio comunitario.
2. Dado que la publicación se realice correctamente, cuando finalice el proceso, entonces debe mostrarse una confirmación.
3. Dado que ocurra un error de publicación, cuando el usuario intente compartir, entonces el sistema debe notificarlo. 102

#### HU45 — Edición de perfil de usuario
**Épica:** EP05  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 102

**Descripción original:** Como usuario, quiero editar mi información personal dentro de HuellaGo, para mantener actualizados mis datos de cuenta.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda a su perfil, cuando seleccione “Editar perfil”, entonces el sistema debe permitir modificar sus datos.
2. Dado que el usuario guarde cambios válidos, cuando confirme la edición, entonces la información debe actualizarse.
3. Dado que existan datos inválidos o vacíos, cuando intente guardar, entonces el sistema debe mostrar validaciones.

#### HU46 — Modificación de hábitos ambientales registrados
**Épica:** EP05  
**Planificación en Arqui Web:** Sprint 2 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 102

**Descripción original:** Como usuario, quiero actualizar mis hábitos ambientales cuando cambie mi rutina, para recalcular mi huella de carbono con datos actuales.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda a la configuración ambiental, cuando modifique sus hábitos, entonces el sistema debe guardar la nueva información.
2. Dado que el usuario finalice la actualización, cuando confirme cambios, entonces debe habilitarse el recálculo de huella.
3. Dado que existan campos incompletos, cuando intente guardar, entonces el sistema debe solicitar completar la información.

#### HU47 — Gestión de preferencias de notificaciones
**Épica:** EP05  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 102

**Descripción original:** Como usuario, quiero activar o desactivar las notificaciones internas de HuellaGo, para controlar la frecuencia de alertas que recibo.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda a configuración, cuando seleccione preferencias de notificación, entonces debe poder activarlas o desactivarlas.
2. Dado que el usuario guarde sus preferencias, cuando confirme cambios, entonces el sistema debe aplicarlos.
3. Dado que las notificaciones estén desactivadas, cuando se generen alertas, entonces no deben mostrarse emergentes. 103

#### HU48 — Configuración de privacidad de cuenta
**Épica:** EP05  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 103

**Descripción original:** Como usuario, quiero gestionar el nivel de visibilidad de mi información comunitaria, para decidir qué datos compartir con otros usuarios.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda a privacidad, cuando visualice la configuración, entonces debe poder elegir el nivel de exposición de su perfil.
2. Dado que el usuario modifique sus preferencias, cuando guarde cambios, entonces el sistema debe aplicarlos correctamente.
3. Dado que el usuario seleccione modo privado, cuando participe en rankings o comunidad, entonces sus datos deben mostrarse limitados.

#### HU49 — Cierre de sesión
**Épica:** EP05  
**Planificación en Arqui Web:** Sprint 1 (Arqui Web)  
**Fuente original:** IHC FINAL.pdf, p. 103

**Descripción original:** Como usuario, quiero cerrar sesión de manera segura dentro de HuellaGo, para finalizar mi acceso a la aplicación.

**Criterios de aceptación originales:**

1. Dado que el usuario acceda al menú de perfil, cuando seleccione “Cerrar sesión”, entonces el sistema debe finalizar su sesión activa.
2. Dado que la sesión se cierre correctamente, cuando termine el proceso, entonces debe redirigirse al login o landing.
3. Dado que ocurra un error al cerrar sesión, cuando el usuario intente salir, entonces el sistema debe mostrar una notificación.

#### HU50 — Retorno a landing page informativa
**Épica:** EP05  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 103

**Descripción original:** Como visitante o usuario, quiero poder regresar a la landing informativa de HuellaGo, para volver a consultar información general del producto.

**Criterios de aceptación originales:**

1. Dado que el usuario se encuentre autenticado o en páginas internas, cuando seleccione el logo o enlace principal, entonces debe poder regresar a la landing.
2. Dado que el usuario cierre sesión, cuando finalice el proceso, entonces el sistema debe redirigirlo automáticamente a la landing.
3. Dado que la landing cargue nuevamente, cuando el usuario navegue por ella, entonces debe poder visualizar toda la información inicial del producto. 104

### EP06 — Módulo Institucional y Monitoreo Empresarial

#### HU51 — Registro de institución afiliada
**Épica:** EP06  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 104

**Descripción original:** Como representante de una universidad o empresa, quiero registrar mi institución dentro de HuellaGo, para acceder a herramientas de monitoreo sostenible grupal.

**Criterios de aceptación originales:**

1. Dado que el representante acceda al portal institucional, cuando complete el formulario de afiliación, entonces el sistema debe registrar la institución.
2. Dado que los datos institucionales sean válidos, cuando confirme el registro, entonces debe habilitarse el acceso administrativo.
3. Dado que existan campos incompletos o inválidos, cuando intente registrarse, entonces el sistema debe mostrar validaciones.

#### HU52 — Visualización de dashboard institucional
**Épica:** EP06  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 104

**Descripción original:** Como administrador institucional, quiero visualizar un dashboard grupal con métricas ambientales colectivas, para supervisar el impacto sostenible de mis miembros.

**Criterios de aceptación originales:**

1. Dado que el administrador inicie sesión, cuando acceda al panel institucional, entonces el sistema debe mostrar métricas grupales resumidas.
2. Dado que existan usuarios vinculados a la institución, cuando cargue el dashboard, entonces debe visualizar huella promedio, progreso y participación.
3. Dado que aún no existan registros suficientes, cuando se consulte el panel, entonces debe mostrarse una notificación informativa.

#### HU53 — Monitoreo de ranking por miembros o equipos
**Épica:** EP06  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 104

**Descripción original:** Como administrador institucional, quiero visualizar rankings de usuarios, áreas o facultades con mejor desempeño ambiental, para incentivar la competencia sostenible.

**Criterios de aceptación originales:**

1. Dado que el administrador acceda a rankings, cuando cargue la sección, entonces el sistema debe mostrar a los miembros ordenados por desempeño.
2. Dado que existan grupos internos registrados, cuando se visualicen los datos, entonces debe poder compararse por equipos o facultades.
3. Dado que no existan suficientes participantes, cuando se consulte el ranking, entonces debe mostrarse un mensaje informativo. 105

#### HU54 — Creación de campañas ecológicas institucionales
**Épica:** EP06  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 105

**Descripción original:** Como administrador institucional, quiero crear campañas o retos colectivos para mis miembros, para promover objetivos ambientales dentro de mi organización.

**Criterios de aceptación originales:**

1. Dado que el administrador acceda al módulo de campañas, cuando seleccione “Crear campaña”, entonces el sistema debe mostrar el formulario de configuración.
2. Dado que el administrador complete nombre, duración y objetivo, cuando guarde la campaña, entonces debe publicarse para los usuarios vinculados.
3. Dado que existan datos incompletos, cuando intente crearla, entonces el sistema debe mostrar validaciones.

#### HU55 — Generación de reportes colectivos descargables
**Épica:** EP06  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 105

**Descripción original:** Como administrador institucional, quiero descargar reportes de desempeño ambiental grupal, para analizar externamente los avances sostenibles de mi organización.

**Criterios de aceptación originales:**

1. Dado que el administrador acceda a reportes, cuando seleccione “Descargar”, entonces el sistema debe generar un archivo institucional.
2. Dado que existan métricas registradas, cuando se exporte el documento, entonces debe incluir huella promedio, participación y resultados de campañas.
3. Dado que ocurra un error en la descarga, cuando intente exportarlo, entonces el sistema debe notificarlo.

#### HU56 — Seguimiento de reducción de impacto colectivo
**Épica:** EP06  
**Planificación en Arqui Web:** No asignada en las dos tablas de Sprint Backlog de Arqui Web  
**Fuente original:** IHC FINAL.pdf, p. 105

**Descripción original:** Como administrador institucional, quiero monitorear la reducción de huella de carbono acumulada por mis miembros, para evaluar el impacto ambiental conjunto de la institución.

**Criterios de aceptación originales:**

1. Dado que existan usuarios activos vinculados, cuando el administrador consulte el progreso colectivo, entonces el sistema debe mostrar reducción total acumulada.
2. Dado que se ejecuten campañas ecológicas, cuando se visualicen sus resultados, entonces debe reflejarse el aporte grupal.
3. Dado que no existan suficientes acciones registradas, cuando se consulte el seguimiento, entonces el sistema debe mostrar una advertencia.

## Formato obligatorio para el dictamen de Codex

Para las **56** HU, producir una matriz con: HU, épica, sprint según Arqui (1 / 2 / no asignada), descripción de requisito backend, clasificación (backend / frontend / mixta), cada criterio (1,2,3) con marca backend aplicable sí/no, evidencia de cumplimiento real (archivo:línea, endpoint, repositorio, tests), estado (completa/parcial/no implementada/no verificable), defectos y pruebas faltantes. Separar conclusiones para Sprint 1, Sprint 2 y otras 31 HU; no llamar «backend completo» al cumplimiento exclusivo de las 25 HU de los sprints sin analizar las restantes 31 y acordar alcance final.

