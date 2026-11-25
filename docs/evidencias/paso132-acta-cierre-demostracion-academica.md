# PASO132 — Acta de cierre de la demostración académica

Fecha:2026-11-24, America/Lima. Última verificación física:2026-11-24T14:10:39.096146-05:00.

## 1. Declaración de cierre técnico

Se deja constancia del **CIERRE TÉCNICO DE LA DEMOSTRACIÓN ACADÉMICA LOCAL**, exclusivamente dentro del alcance autorizado y sobre datos sintéticos. Los cuatro endpoints existentes cuentan con evidencia suficiente para el recorrido acotado y las14 peticiones manuales previstas finalizaron con sus códigos esperados, sin repetirlas.

Esta es una constancia técnica basada en archivos, resultados comunicados por el responsable y lecturas reales PostgreSQL; no es una firma del profesor, una decisión de aprobación científica, una acreditación editorial productiva o aceptación formal de todas las HU. No se atribuye al responsable una decisión adicional distinta de las autorizaciones expresas ya comunicadas.

## 2. Alcance y procedencia

- AplicacionDemoIA127, composición exclusiva de src/test, controlador habitual de HuellaGo en127.0.0.1:8081 y JWT del usuario sintético91.
- Base exclusiva huellagodb_ia_integracion_test, localhost:5432, rol huellago_ia119_test sin privilegios administrativos. No se utilizó huellagodb ni huellagodb_hu29_test.
- Familia limitada TR_PLANIFICAR_DESPLAZAMIENTOS/1, tres variantes autorizadas solo para demo y medio Auto. Ninguna autorización editorial real nueva o ampliación a otras familias.
-124: generación inicial real mediante Qwen3/Ollama, guardado29;128: controlador habitual y reutilización real. Son evidencias confirmadas por el responsable, no reejecutadas aquí.
-130B: asociaciones30/31 preparadas manualmente para aceptación de snapshots, método PREPARACION_SINTETICA_MANUAL_130B/modeloNULL. **La asociación30 no procede de una inferencia nueva.**
-131:14 peticiones manuales de autenticación, historial, conversión/completado y sus negativos. Las dos últimas404 fueron comunicadas al iniciar132 y se consolidan en el documento nuevo, sin sobrescribir131.
-132: una verificación final real de solo lectura PostgreSQL, UTF-8,19 condiciones verdaderas y cierre ROLLBACK. Sin HTTP, Maven, Ollama, SQL de escritura ni detención automática del servidor.

## 3. Resultados acreditados

| Elemento | Evidencia/resultado |
|---|---|
| Códigos de las14 peticiones |200,200,201,409,200,409,200,401,401,401,404,404,404,404;14/14 |
| Generación/reutilización |Demostradas previamente124/128; idempotencia no se reejecutó después de realizada30 |
| Conversión de snapshot |201 crea solo reto3/participación3; copia exacta del título/descripcion de30, INDIVIDUAL/categoría61, activo, recompensa0 |
| Duplicados |Segundo POST409 y segundo PUT409;1 reto/1 participación, fechas y textos originales conservados |
| Completado |30 completada; fecha física2026-11-24T13:49:14.797937, fechaGeneracion original2026-11-24T13:23:49.75312, snapshot/metadatos intactos |
| Historial |Vacío inicialmente; después únicamente30 completada con sus textos/fechas. No se incluyen pendientes29/31 |
| Autenticación/propiedad |Sin JWT401 en tres rutas; recurso ajeno31 devuelve404 en completar/convertir sin efectos. Página HTML401 de Tomcat registrada como tal |
| Inexistencia |ID1 elegido mediante SELECT y aún ausente; dos404, sin registros creados ni secuencias reservadas |
| Integridad protegida |29 y canónica29 JSONB iguales a130A;31 igual a130B; hábitos intactos. Cero retos para origen1/29/31 |
| Puntos/retos |Usuarios91/92 con0 ecopuntos/nivel1; reto3 y participación3 íntegros/activos; progreso0, sin completado de participación |

Conteos finales reales: **2 usuarios,3 asociaciones,1 canónica,3 hábitos,1 reto,1 participación y4 sesiones**. Las3 sesiones anteriores más la nueva sesión4 del91 a13:39:34.415382 explican la diferencia por autenticación autorizada. No se consultaron/exportaron tokens y no se eliminaron sesiones.

## 4. Límites del cierre

1. **No se cierran automáticamente todas las HU31–HU37.** Matriz y plan adjuntos especifican los CA pendientes: focos/información insuficiente, lista/orden múltiple, impacto esperado/estimado, presentación y evidencia integral HU37.
2. La exclusión del31 pendiente del historial no prueba por sí sola el filtro de asociaciones ajenas **completadas**. La propiedad de ese recurso sí se ejercitó por PUT/POST404. No se crea/completa otra asociación para ampliar esta evidencia.
3. Hay una sola completada: no se verificó por HTTP ordenamiento o desempate de múltiples completadas. La cobertura de repositorio/tests se mantiene diferenciada.
4.14/14 códigos no es una garantía estadística de calidad de Qwen3. La composición usa IA para selección y Java para validación/textos controlados; no es redacción libre certificada. El requisito académico de ese reparto sigue requiriendo confirmación del profesor.
5. No se completó el reto3 ni se probaron más funcionalidades de gamificación; no se otorgaron puntos. Tampoco se amplió este cierre al frontend no observado o a usuarios reales.

## 5. Seguridad y efectos futuros

LEGADO permanece predeterminado. DEMO sigue en src/test, con controles de destino/JWT91/Auto/huellas y sin configuración productiva. PUBLICABLE sigue rechazado en coordinador y escritor; registro editorial productivo vacío. BORRADOR/PENDIENTE_REVISION/publicable=false permanecen como situación editorial del escenario. No se introdujo activación general ni se habilitó92.

HU37 permanece determinista y el listener conserva BEFORE_COMMIT→RecomendacionServiceImpl.generar(Usuario), sin invocar Ollama. No se cambió el listener ni se registró en la demo. No se pusieron inferencias dentro de transacciones heredadas.

La acción TR_PLANIFICAR_DESPLAZAMIENTOS/1 de30 cuenta realizada para91 según la captura existente, cambiando la huella/elegibilidad de una lectura futura. Preservar29 no garantiza idéntico contexto de generación. No se ejecutó POST /generar para comprobarlo y no debe esperarse el mismo resultado128 después del completado.

## 6. Conservación e integridad local

Solo se crearon estos cinco documentos dentro de target/paso132:

1. verificacion-final-postgresql.md
2. resultados-finales-postman.md
3. matriz-aceptacion-actualizada.md
4. criterios-pendientes-y-plan-minimo.md
5. acta-cierre-demostracion-academica.md

Se cotejaron hashes antes/después de **382 archivos** de src, db, pom.xml y evidencias129/130A/130B/131, sin cambios. Huella SHA-256 del inventario ordenado:

```text
1157173EF95BC033010C31D4E6D0457B40C9F681B2881FD5BBCF5ED2ADD3C797
```

No se modificó código, pruebas, configuración, catálogo, plantillas, entidades, repositorios, migraciones, índices o HU37. No se aprobaron variantes, no se restauraron estados ni se limpiaron registros. No hubo escrituras Git ni cambio de R-Carlos. No se guardaron contraseñas, JWT o contenido/ruta del pgpass en los entregables.

## 7. Servidor y siguiente trabajo

**Sí: el responsable puede detener manualmente el servidor académico con Ctrl+C en su terminal.** La ronda prevista terminó y la transacción de verificación está cerrada. El asistente no lo detuvo; no se ha verificado ni se presume un apagado posterior. Detenerlo no autoriza borrar fixtures o sesiones.

Para completar el módulo íntegro, el mínimo es resolver/acordar formalmente los CA pendientes de HU31/32/33, comprobar la presentación/alertas del frontend HU34/35/36 y completar o localizar la evidencia heredada HU37. Usar componentes existentes y pruebas dirigidas solo donde haya cambios o evidencia obligatoria faltante. No otra arquitectura, ajustes sucesivos de prompt ni una activación productiva para aparentar cierre.

Se detiene el trabajo al entregar PASO132. No se ejecuta otro paso, limpieza, nueva generación o despliegue automáticamente.

