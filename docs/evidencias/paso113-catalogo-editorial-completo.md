# Catálogo editorial canónico de HuellaGo

10 familias y 30 presentaciones. Estado BORRADOR, revisión PENDIENTE_REVISION y publicable=false. Ningún contenido está aprobado. Los bloques de texto contienen los originales, sin sustitución de variables.

Los dos enfoques se numeran alfabéticamente solo para organización: el catálogo usa un conjunto sin prioridad. El nombre mostrado de cada familia es su título canónico SIN_ENFOQUE, no un campo adicional del catálogo.

## TR_PLANIFICAR_DESPLAZAMIENTOS / 1

Nombre mostrado: Comprueba cómo organizar tus gestiones

Categoría: Transporte

Objetivo original:

```text
Revisar oportunidades de planificación del transporte registrado.
```

Descripción candidata del catálogo (distinta de las presentaciones):

```text
Examinar si alguna gestión puede agruparse o evitarse, únicamente si las necesidades y condiciones reales lo permiten.
```

Condiciones canónicas tipadas (todas deben cumplirse):

```text
{
  "/transporte/medio" : [ "Auto", "Bus", "Moto" ]
}
```

Además: categoría completa y válida, evidencias requeridas y acción no realizada según id y versión.

Evidencias requeridas: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Enfoque 1 y enfoque 2 (orden alfabético): AGRUPACION, NECESIDAD

Información adicional permitida: [NECESIDADES_DESPLAZAMIENTO]

Restricciones originales:

```text
No inferir recorridos, horarios o destinos a partir del total semanal.
```

```text
Bus no implica uso de automóvil; no atribuir combustible o ahorro.
```

### AGRUPACION

Identidad: TR_PLANIFICAR_DESPLAZAMIENTOS/1/AGRUPACION/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba cómo organizar tus gestiones
```

Descripción exacta:

```text
Anota tus próximas gestiones y comprueba si dos podrían realizarse en una misma salida sin comprometer tus necesidades.
```

Motivo exacto:

```text
Registraste {/transporte/medio}, {/transporte/kmSemana} km/semana y {/transporte/diasSemana} días/semana. No conocemos tus próximas gestiones.
```

Campos autorizados: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Marcadores presentes: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

SHA-256 canónico: `68ac3e60bc94150cdad8734a486f71094eaca769b7fd2e000ef2280485513977`

### NECESIDAD

Identidad: TR_PLANIFICAR_DESPLAZAMIENTOS/1/NECESIDAD/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba cómo organizar tus gestiones
```

Descripción exacta:

```text
Anota tus próximas gestiones y comprueba cuáles requieren una salida y cuáles podrían resolverse de otra manera, si encuentras una opción adecuada.
```

Motivo exacto:

```text
Registraste {/transporte/medio}, {/transporte/kmSemana} km/semana y {/transporte/diasSemana} días/semana. No conocemos tus próximas gestiones.
```

Campos autorizados: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Marcadores presentes: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

SHA-256 canónico: `5a63fa0c2cafe7e0c728618efc6fb5ba4c7519bf66995ff84bc10e3d12b3dae8`

### SIN_ENFOQUE

Identidad: TR_PLANIFICAR_DESPLAZAMIENTOS/1/SIN_ENFOQUE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba cómo organizar tus gestiones
```

Descripción exacta:

```text
Anota tus próximas gestiones y comprueba qué necesidades debe cubrir cada salida antes de organizarla.
```

Motivo exacto:

```text
Registraste {/transporte/medio}, {/transporte/kmSemana} km/semana y {/transporte/diasSemana} días/semana. No conocemos tus próximas gestiones.
```

Campos autorizados: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Marcadores presentes: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

SHA-256 canónico: `3b650417e025042186bfa88eac1df05a0d67f417809c3bafa6438f65fe804440`

## TR_EXPLORAR_ALTERNATIVAS_AUTO / 1

Nombre mostrado: Comprueba alternativas para una gestión

Categoría: Transporte

Objetivo original:

```text
Comprobar si existen alternativas viables a algún desplazamiento en auto.
```

Descripción candidata del catálogo (distinta de las presentaciones):

```text
Revisar disponibilidad y viabilidad de alternativas antes de considerar sustituir algún desplazamiento; no asumir que existen.
```

Condiciones canónicas tipadas (todas deben cumplirse):

```text
{
  "/transporte/medio" : [ "Auto" ]
}
```

Además: categoría completa y válida, evidencias requeridas y acción no realizada según id y versión.

Evidencias requeridas: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Enfoque 1 y enfoque 2 (orden alfabético): DISPONIBILIDAD, VIABILIDAD

Información adicional permitida: [ALTERNATIVAS_DISPONIBLES]

Restricciones originales:

```text
No afirmar que hay transporte público, bicicleta disponible o trayectos cortos.
```

```text
No atribuir emisiones ni obligar a cambiar de medio.
```

### DISPONIBILIDAD

Identidad: TR_EXPLORAR_ALTERNATIVAS_AUTO/1/DISPONIBILIDAD/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba alternativas para una gestión
```

Descripción exacta:

```text
Elige una próxima gestión y busca información sobre qué alternativas de desplazamiento existen para ella, si encuentras alguna.
```

Motivo exacto:

```text
Registraste {/transporte/medio}, {/transporte/kmSemana} km/semana y {/transporte/diasSemana} días/semana. No conocemos las alternativas disponibles para tus desplazamientos.
```

Campos autorizados: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Marcadores presentes: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

SHA-256 canónico: `f05ba61bdcc6a259131fe9072ed5cc114a9b3c78ed68ee3ff4623c6272b1976c`

### VIABILIDAD

Identidad: TR_EXPLORAR_ALTERNATIVAS_AUTO/1/VIABILIDAD/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba alternativas para una gestión
```

Descripción exacta:

```text
Si identificas una alternativa para una próxima gestión, comprueba si su seguridad, accesibilidad y condiciones permiten utilizarla sin comprometer tus necesidades.
```

Motivo exacto:

```text
Registraste {/transporte/medio}, {/transporte/kmSemana} km/semana y {/transporte/diasSemana} días/semana. No conocemos las alternativas disponibles para tus desplazamientos.
```

Campos autorizados: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Marcadores presentes: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

SHA-256 canónico: `466e3cc4d75558da1778cbc02f94c250b83b93718f7d82b88c45599aa1ec91ac`

### SIN_ENFOQUE

Identidad: TR_EXPLORAR_ALTERNATIVAS_AUTO/1/SIN_ENFOQUE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba alternativas para una gestión
```

Descripción exacta:

```text
Elige una próxima gestión y averigua si existe otra forma de realizar ese desplazamiento que sea adecuada para tus necesidades.
```

Motivo exacto:

```text
Registraste {/transporte/medio}, {/transporte/kmSemana} km/semana y {/transporte/diasSemana} días/semana. No conocemos las alternativas disponibles para tus desplazamientos.
```

Campos autorizados: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Marcadores presentes: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

SHA-256 canónico: `997291bb63f49757d62b36a5016f19662df1421f4998ac1c9cac8cac5d380e4c`

## TR_REVISAR_MOVILIDAD_ACTIVA / 1

Nombre mostrado: Organiza tus gestiones con tu movilidad actual

Categoría: Transporte

Objetivo original:

```text
Revisar la viabilidad de organizar gestiones compatibles con la movilidad activa ya registrada.
```

Descripción candidata del catálogo (distinta de las presentaciones):

```text
Comprobar si la organización de gestiones admite alguna mejora viable; no pedir comenzar ni simplemente continuar caminando o usando bicicleta.
```

Condiciones canónicas tipadas (todas deben cumplirse):

```text
{
  "/transporte/medio" : [ "Bicicleta", "Caminando" ]
}
```

Además: categoría completa y válida, evidencias requeridas y acción no realizada según id y versión.

Evidencias requeridas: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Enfoque 1 y enfoque 2 (orden alfabético): ORGANIZACION, VIABILIDAD

Información adicional permitida: [NECESIDADES_DESPLAZAMIENTO]

Restricciones originales:

```text
No asumir automóvil en otros días ni inventar rutas o distancias por trayecto.
```

```text
No afirmar que recorrer menos o más distancia activa garantiza un beneficio ambiental.
```

### ORGANIZACION

Identidad: TR_REVISAR_MOVILIDAD_ACTIVA/1/ORGANIZACION/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Organiza tus gestiones con tu movilidad actual
```

Descripción exacta:

```text
Anota tus próximas gestiones y comprueba cuáles podrías organizar en una misma salida con el medio que ya utilizas.
```

Motivo exacto:

```text
Registraste {/transporte/medio}, {/transporte/kmSemana} km/semana y {/transporte/diasSemana} días/semana. No conocemos tus próximas gestiones.
```

Campos autorizados: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Marcadores presentes: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

SHA-256 canónico: `85d3f8c129992cde6698d292773279d75e85c1b2a3fd46ed531ebbf1413623cf`

### VIABILIDAD

Identidad: TR_REVISAR_MOVILIDAD_ACTIVA/1/VIABILIDAD/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Organiza tus gestiones con tu movilidad actual
```

Descripción exacta:

```text
Elige una próxima gestión y comprueba si su horario, carga y condiciones permiten realizarla con el medio que ya utilizas.
```

Motivo exacto:

```text
Registraste {/transporte/medio}, {/transporte/kmSemana} km/semana y {/transporte/diasSemana} días/semana. No conocemos tus próximas gestiones.
```

Campos autorizados: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Marcadores presentes: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

SHA-256 canónico: `a6e222cab250c20104259ffbf64d742eecfc04f330f06cd993cfc81630aa6a36`

### SIN_ENFOQUE

Identidad: TR_REVISAR_MOVILIDAD_ACTIVA/1/SIN_ENFOQUE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Organiza tus gestiones con tu movilidad actual
```

Descripción exacta:

```text
Anota una próxima gestión y comprueba qué necesitas para realizarla con el medio que ya utilizas, considerando horario, carga y seguridad.
```

Motivo exacto:

```text
Registraste {/transporte/medio}, {/transporte/kmSemana} km/semana y {/transporte/diasSemana} días/semana. No conocemos tus próximas gestiones.
```

Campos autorizados: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

Marcadores presentes: [/transporte/diasSemana, /transporte/kmSemana, /transporte/medio]

SHA-256 canónico: `6c49160965208d3c79e31367a16c7993474edf45b3504012bddc0854adf0004a`

## EN_REVISAR_USOS / 1

Nombre mostrado: Identifica usos de tu fuente energética

Categoría: Energía

Objetivo original:

```text
Identificar usos del hogar que podrían revisarse sin diagnosticar derroche.
```

Descripción candidata del catálogo (distinta de las presentaciones):

```text
Revisar los usos reales de la fuente registrada y comprobar si hay algún consumo innecesario que pueda evitarse con seguridad.
```

Condiciones canónicas tipadas (todas deben cumplirse):

```text
{
  "/energia/fuente" : [ "Electricidad", "Gas", "Solar" ]
}
```

Además: categoría completa y válida, evidencias requeridas y acción no realizada según id y versión.

Evidencias requeridas: [/energia/fuente, /energia/personas, /energia/vivienda]

Enfoque 1 y enfoque 2 (orden alfabético): COMPROBAR_NECESIDAD, IDENTIFICAR_USOS

Información adicional permitida: [USOS_REALES_HOGAR]

Restricciones originales:

```text
No asumir equipos concretos, uso continuo o consumos altos.
```

```text
No exigir compras ni desconexiones que comprometan seguridad o necesidades esenciales.
```

```text
No atribuir origen renovable o fósil a Electricidad.
```

### COMPROBAR_NECESIDAD

Identidad: EN_REVISAR_USOS/1/COMPROBAR_NECESIDAD/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Identifica usos de tu fuente energética
```

Descripción exacta:

```text
Elige un uso real de la fuente registrada ({/energia/fuente}) y comprueba qué necesidad satisface. Considera un ajuste solo si mantiene la seguridad, el bienestar y las necesidades esenciales.
```

Motivo exacto:

```text
Registraste vivienda {/energia/vivienda}, {/energia/personas} personas y fuente {/energia/fuente}. No conocemos los usos concretos de esa fuente en tu hogar.
```

Campos autorizados: [/energia/fuente, /energia/personas, /energia/vivienda]

Marcadores presentes: [/energia/fuente, /energia/personas, /energia/vivienda]

SHA-256 canónico: `ed9f56940b272701d70b5579918abe82939c13f50c8f4fd7cbd810b0d8628139`

### IDENTIFICAR_USOS

Identidad: EN_REVISAR_USOS/1/IDENTIFICAR_USOS/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Identifica usos de tu fuente energética
```

Descripción exacta:

```text
Anota las actividades de tu hogar que usan la fuente registrada ({/energia/fuente}) y elige una para describir cómo la utilizas.
```

Motivo exacto:

```text
Registraste vivienda {/energia/vivienda}, {/energia/personas} personas y fuente {/energia/fuente}. No conocemos los usos concretos de esa fuente en tu hogar.
```

Campos autorizados: [/energia/fuente, /energia/personas, /energia/vivienda]

Marcadores presentes: [/energia/fuente, /energia/personas, /energia/vivienda]

SHA-256 canónico: `54f083cebf7393010c383d459d161b756c6c3ec36649f98042891c3753cc25ae`

### SIN_ENFOQUE

Identidad: EN_REVISAR_USOS/1/SIN_ENFOQUE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Identifica usos de tu fuente energética
```

Descripción exacta:

```text
Anota un uso real de la fuente registrada ({/energia/fuente}) en tu hogar y comprueba qué necesidad satisface antes de considerar algún cambio.
```

Motivo exacto:

```text
Registraste vivienda {/energia/vivienda}, {/energia/personas} personas y fuente {/energia/fuente}. No conocemos los usos concretos de esa fuente en tu hogar.
```

Campos autorizados: [/energia/fuente, /energia/personas, /energia/vivienda]

Marcadores presentes: [/energia/fuente, /energia/personas, /energia/vivienda]

SHA-256 canónico: `f652f91fd9fe5327faf76d69943f57c26d341a12c558d5e47ffa5996148fba37`

## EN_REVISAR_REGISTRO_HOGAR / 1

Nombre mostrado: Comprueba tu registro eléctrico

Categoría: Energía

Objetivo original:

```text
Revisar el alcance del consumo eléctrico diario registrado del hogar.
```

Descripción candidata del catálogo (distinta de las presentaciones):

```text
Comprobar cómo se obtuvo el registro y qué periodo y alcance describe antes de formular conclusiones sobre cambios de consumo.
```

Condiciones canónicas tipadas (todas deben cumplirse):

```text
{
  "/energia/fuente" : [ "Electricidad" ]
}
```

Además: categoría completa y válida, evidencias requeridas y acción no realizada según id y versión.

Evidencias requeridas: [/energia/consumoKwhDiario, /energia/fuente, /energia/personas, /energia/vivienda]

Enfoque 1 y enfoque 2 (orden alfabético): ALCANCE, PROCEDENCIA_REGISTRO

Información adicional permitida: [PROCEDENCIA_CONSUMO]

Restricciones originales:

```text
Requiere consumo registrado; cero es válido y ausencia no equivale a cero.
```

```text
Conservar kWh/día y ámbito HOGAR, sin dividir o multiplicar por habitantes.
```

```text
No inferir tendencias, dispositivos, intensidad ni ahorros.
```

### ALCANCE

Identidad: EN_REVISAR_REGISTRO_HOGAR/1/ALCANCE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba tu registro eléctrico
```

Descripción exacta:

```text
Consulta qué periodo y qué parte del hogar cubría la medición o estimación original utilizada para obtener tu registro en kWh/día del hogar.
```

Motivo exacto:

```text
Registraste vivienda {/energia/vivienda}, {/energia/personas} personas y {/energia/fuente}, con {/energia/consumoKwhDiario} kWh/día del hogar. No tenemos información sobre cómo se obtuvo ese valor, qué medición o estimación lo respalda ni su periodo original.
```

Campos autorizados: [/energia/consumoKwhDiario, /energia/fuente, /energia/personas, /energia/vivienda]

Marcadores presentes: [/energia/consumoKwhDiario, /energia/fuente, /energia/personas, /energia/vivienda]

SHA-256 canónico: `477459df66140b166d5c504c4b69d427596e550ad1d9ece922a9cec91ba289fe`

### PROCEDENCIA_REGISTRO

Identidad: EN_REVISAR_REGISTRO_HOGAR/1/PROCEDENCIA_REGISTRO/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba tu registro eléctrico
```

Descripción exacta:

```text
Consulta de dónde obtuviste el valor del consumo eléctrico y anota cómo se calculó o midió, si dispones de esa información.
```

Motivo exacto:

```text
Registraste vivienda {/energia/vivienda}, {/energia/personas} personas y {/energia/fuente}, con {/energia/consumoKwhDiario} kWh/día del hogar. No tenemos información sobre cómo se obtuvo ese valor, qué medición o estimación lo respalda ni su periodo original.
```

Campos autorizados: [/energia/consumoKwhDiario, /energia/fuente, /energia/personas, /energia/vivienda]

Marcadores presentes: [/energia/consumoKwhDiario, /energia/fuente, /energia/personas, /energia/vivienda]

SHA-256 canónico: `4479aa99990228eae9651341ff121bd6b6b4fb5aeeaac5d1c9c05fcbc6d7ae39`

### SIN_ENFOQUE

Identidad: EN_REVISAR_REGISTRO_HOGAR/1/SIN_ENFOQUE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba tu registro eléctrico
```

Descripción exacta:

```text
Consulta de dónde obtuviste el registro eléctrico del hogar y comprueba el periodo y la cobertura de la medición o estimación original.
```

Motivo exacto:

```text
Registraste vivienda {/energia/vivienda}, {/energia/personas} personas y {/energia/fuente}, con {/energia/consumoKwhDiario} kWh/día del hogar. No tenemos información sobre cómo se obtuvo ese valor, qué medición o estimación lo respalda ni su periodo original.
```

Campos autorizados: [/energia/consumoKwhDiario, /energia/fuente, /energia/personas, /energia/vivienda]

Marcadores presentes: [/energia/consumoKwhDiario, /energia/fuente, /energia/personas, /energia/vivienda]

SHA-256 canónico: `e906f1aab844f6664c04235b5958adb894b9983affc8c17fa19348199a2844ca`

## AL_PLANIFICAR_APROVECHAMIENTO / 1

Nombre mostrado: Consulta lo disponible antes de comprar

Categoría: Alimentación

Objetivo original:

```text
Revisar oportunidades de aprovechamiento de alimentos respetando el tipo registrado.
```

Descripción candidata del catálogo (distinta de las presentaciones):

```text
Revisar lo que realmente hay disponible antes de planificar compras o preparaciones, sin atribuir alimentos ni desperdicios existentes.
```

Condiciones canónicas tipadas (todas deben cumplirse):

```text
{
  "/alimentacion/tipo" : [ "Mixta", "Vegana", "Vegetariana" ]
}
```

Además: categoría completa y válida, evidencias requeridas y acción no realizada según id y versión.

Evidencias requeridas: [/alimentacion/tipo]

Enfoque 1 y enfoque 2 (orden alfabético): DISPONIBILIDAD, PLANIFICACION

Información adicional permitida: [ALIMENTOS_DISPONIBLES]

Restricciones originales:

```text
No prescribir carne ni productos animales como obligación.
```

```text
No formular diagnósticos, carencias o recomendaciones nutricionales o médicas.
```

```text
No inventar alimentos, cantidades, frecuencias o emisiones.
```

### DISPONIBILIDAD

Identidad: AL_PLANIFICAR_APROVECHAMIENTO/1/DISPONIBILIDAD/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Consulta lo disponible antes de comprar
```

Descripción exacta:

```text
Anota los alimentos que realmente tienes disponibles y comprueba cuáles puedes utilizar respetando tu tipo de alimentación.
```

Motivo exacto:

```text
Tu alimentación registrada es {/alimentacion/tipo}. No conocemos qué alimentos tienes disponibles.
```

Campos autorizados: [/alimentacion/tipo]

Marcadores presentes: [/alimentacion/tipo]

SHA-256 canónico: `36cbf9b312936517260db6f0259da6f959e7df5f7f9e9373035a20d071c08d0a`

### PLANIFICACION

Identidad: AL_PLANIFICAR_APROVECHAMIENTO/1/PLANIFICACION/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Consulta lo disponible antes de comprar
```

Descripción exacta:

```text
Antes de preparar tu próxima lista de compras, consulta qué alimentos tienes disponibles y considera cuáles puedes aprovechar respetando tu tipo de alimentación.
```

Motivo exacto:

```text
Tu alimentación registrada es {/alimentacion/tipo}. No conocemos qué alimentos tienes disponibles.
```

Campos autorizados: [/alimentacion/tipo]

Marcadores presentes: [/alimentacion/tipo]

SHA-256 canónico: `2226c3742ad9f3294bfa35e4d1cede9e30c4ffcbcce18076dbe6c976a30ec413`

### SIN_ENFOQUE

Identidad: AL_PLANIFICAR_APROVECHAMIENTO/1/SIN_ENFOQUE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Consulta lo disponible antes de comprar
```

Descripción exacta:

```text
Comprueba qué alimentos tienes disponibles antes de preparar tu próxima lista de compras, respetando tu tipo de alimentación.
```

Motivo exacto:

```text
Tu alimentación registrada es {/alimentacion/tipo}. No conocemos qué alimentos tienes disponibles.
```

Campos autorizados: [/alimentacion/tipo]

Marcadores presentes: [/alimentacion/tipo]

SHA-256 canónico: `bac673f8d3e8a18c719c1615586aeac1792ada5d7074831331430994595d5de6`

## AL_REVISAR_PRESENTACION / 1

Nombre mostrado: Consulta las presentaciones de un alimento

Categoría: Alimentación

Objetivo original:

```text
Examinar opciones de presentación compatibles con la alimentación registrada.
```

Descripción candidata del catálogo (distinta de las presentaciones):

```text
Si hay opciones disponibles y adecuadas, comparar su presentación y envases antes de elegir; no cambiar obligatoriamente el tipo de alimentación.
```

Condiciones canónicas tipadas (todas deben cumplirse):

```text
{
  "/alimentacion/tipo" : [ "Mixta", "Vegana", "Vegetariana" ]
}
```

Además: categoría completa y válida, evidencias requeridas y acción no realizada según id y versión.

Evidencias requeridas: [/alimentacion/tipo]

Enfoque 1 y enfoque 2 (orden alfabético): DISPONIBILIDAD, PRESENTACION

Información adicional permitida: [OPCIONES_ALIMENTARIAS_DISPONIBLES]

Restricciones originales:

```text
No asumir comercios, alimentos, presupuesto o alternativas disponibles.
```

```text
No equiparar automáticamente menor envase con menor huella ni inventar ahorros.
```

```text
No prescribir dietas ni consumo animal obligatorio.
```

### DISPONIBILIDAD

Identidad: AL_REVISAR_PRESENTACION/1/DISPONIBILIDAD/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Consulta las presentaciones de un alimento
```

Descripción exacta:

```text
Para un alimento que estés considerando, averigua si hay otras presentaciones disponibles que sean adecuadas para tus necesidades y tu tipo de alimentación.
```

Motivo exacto:

```text
Tu alimentación registrada es {/alimentacion/tipo}. No conocemos los alimentos que estás considerando ni sus presentaciones disponibles.
```

Campos autorizados: [/alimentacion/tipo]

Marcadores presentes: [/alimentacion/tipo]

SHA-256 canónico: `3a228c68261357eb7bf48e8595c38a7e0bbbde428923b7caef78111fa30b3512`

### PRESENTACION

Identidad: AL_REVISAR_PRESENTACION/1/PRESENTACION/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Consulta las presentaciones de un alimento
```

Descripción exacta:

```text
Si encuentras distintas presentaciones de un alimento que estés considerando, compara su envase y la cantidad que necesitas, respetando tu tipo de alimentación.
```

Motivo exacto:

```text
Tu alimentación registrada es {/alimentacion/tipo}. No conocemos los alimentos que estás considerando ni sus presentaciones disponibles.
```

Campos autorizados: [/alimentacion/tipo]

Marcadores presentes: [/alimentacion/tipo]

SHA-256 canónico: `2eb32a15baddba02278b5b981cee790f5b7d7f7abd965386b01fb2a5f7c4dc1b`

### SIN_ENFOQUE

Identidad: AL_REVISAR_PRESENTACION/1/SIN_ENFOQUE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Consulta las presentaciones de un alimento
```

Descripción exacta:

```text
Para un alimento que estés considerando y sea compatible con tu alimentación, consulta qué presentaciones hay y cómo es su envase, si encuentras opciones disponibles.
```

Motivo exacto:

```text
Tu alimentación registrada es {/alimentacion/tipo}. No conocemos los alimentos que estás considerando ni sus presentaciones disponibles.
```

Campos autorizados: [/alimentacion/tipo]

Marcadores presentes: [/alimentacion/tipo]

SHA-256 canónico: `1633aa0b146d698d7961cdc48114b3e3be54f7a5cb2ee8ead2e502a4383cfeff`

## RE_INICIAR_SEPARACION / 1

Nombre mostrado: Averigua cómo entregar materiales separados

Categoría: Residuos

Objetivo original:

```text
Explorar el inicio de separación de materiales para su posible recuperación.
```

Descripción candidata del catálogo (distinta de las presentaciones):

```text
Comprobar las condiciones locales y considerar separar los materiales admitidos, si existe una vía de gestión disponible.
```

Condiciones canónicas tipadas (todas deben cumplirse):

```text
{
  "/residuos/reciclas" : [ false ]
}
```

Además: categoría completa y válida, evidencias requeridas y acción no realizada según id y versión.

Evidencias requeridas: [/residuos/plasticos, /residuos/reciclas]

Enfoque 1 y enfoque 2 (orden alfabético): CONDICIONES_LOCALES, SEPARACION

Información adicional permitida: [GESTION_LOCAL_DISPONIBLE]

Restricciones originales:

```text
reciclas=false no demuestra ausencia de toda separación previa.
```

```text
No garantizar recogida ni reciclaje efectivo; no aplicable cuando reciclas=true.
```

### CONDICIONES_LOCALES

Identidad: RE_INICIAR_SEPARACION/1/CONDICIONES_LOCALES/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Averigua cómo entregar materiales separados
```

Descripción exacta:

```text
Busca información sobre una posible vía de gestión de reciclables en tu zona. Si encuentras una disponible, consulta sus condiciones de recepción.
```

Motivo exacto:

```text
Registro de reciclaje: {/residuos/reciclas}; uso de plásticos: {/residuos/plasticos}. No conocemos las opciones de gestión de tu zona.
```

Campos autorizados: [/residuos/plasticos, /residuos/reciclas]

Marcadores presentes: [/residuos/plasticos, /residuos/reciclas]

SHA-256 canónico: `c367dd2ebfee85d8af5325353759a390d5c64a604fe9bf190c56adb9fbc2b9c8`

### SEPARACION

Identidad: RE_INICIAR_SEPARACION/1/SEPARACION/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Averigua cómo entregar materiales separados
```

Descripción exacta:

```text
Si encuentras una vía de gestión disponible, consulta qué materiales admite e identifica alguno que puedas separar siguiendo sus indicaciones.
```

Motivo exacto:

```text
Registro de reciclaje: {/residuos/reciclas}; uso de plásticos: {/residuos/plasticos}. No conocemos las opciones de gestión de tu zona.
```

Campos autorizados: [/residuos/plasticos, /residuos/reciclas]

Marcadores presentes: [/residuos/plasticos, /residuos/reciclas]

SHA-256 canónico: `6b3e97ba53a4148e7ffce1913fa475e184596c20d4cc2edd0653da794bb09fce`

### SIN_ENFOQUE

Identidad: RE_INICIAR_SEPARACION/1/SIN_ENFOQUE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Averigua cómo entregar materiales separados
```

Descripción exacta:

```text
Busca información sobre posibles vías de gestión de reciclables en tu zona y, si encuentras alguna, consulta qué materiales admite.
```

Motivo exacto:

```text
Registro de reciclaje: {/residuos/reciclas}; uso de plásticos: {/residuos/plasticos}. No conocemos las opciones de gestión de tu zona.
```

Campos autorizados: [/residuos/plasticos, /residuos/reciclas]

Marcadores presentes: [/residuos/plasticos, /residuos/reciclas]

SHA-256 canónico: `b9e960249df99574453e685be3539e7f9b0394c6197f227223ff7f284c24b3f2`

## RE_REVISAR_ENTREGA / 1

Nombre mostrado: Consulta las indicaciones para tu entrega

Categoría: Residuos

Objetivo original:

```text
Revisar condiciones de entrega complementarias al reciclaje registrado.
```

Descripción candidata del catálogo (distinta de las presentaciones):

```text
Consultar qué materiales acepta la vía de gestión utilizada y revisar si la separación es compatible con esas indicaciones.
```

Condiciones canónicas tipadas (todas deben cumplirse):

```text
{
  "/residuos/reciclas" : [ true ]
}
```

Además: categoría completa y válida, evidencias requeridas y acción no realizada según id y versión.

Evidencias requeridas: [/residuos/plasticos, /residuos/reciclas]

Enfoque 1 y enfoque 2 (orden alfabético): CONDICIONES_ENTREGA, MATERIALES_ADMITIDOS

Información adicional permitida: [GESTION_LOCAL_DISPONIBLE]

Restricciones originales:

```text
Reconocer que ya recicla; no limitarse a mantenerlo ni proponer iniciarlo.
```

```text
No asumir tipo de plástico, eficiencia o recuperación efectiva.
```

### CONDICIONES_ENTREGA

Identidad: RE_REVISAR_ENTREGA/1/CONDICIONES_ENTREGA/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Consulta las indicaciones para tu entrega
```

Descripción exacta:

```text
Si entregas materiales a una persona, organización o servicio, consulta sus indicaciones de preparación y entrega.
```

Motivo exacto:

```text
Registraste que reciclas y un uso de plásticos {/residuos/plasticos}. No conocemos cómo lo realizas ni las indicaciones de una posible vía de entrega.
```

Campos autorizados: [/residuos/plasticos, /residuos/reciclas]

Marcadores presentes: [/residuos/plasticos]

SHA-256 canónico: `cac6f577532a4d850e29ec55eed0d9d50743d6328e6785136be616556802eae7`

### MATERIALES_ADMITIDOS

Identidad: RE_REVISAR_ENTREGA/1/MATERIALES_ADMITIDOS/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Consulta las indicaciones para tu entrega
```

Descripción exacta:

```text
Si utilizas una vía de entrega, consulta qué materiales admite antes de preparar la próxima entrega.
```

Motivo exacto:

```text
Registraste que reciclas y un uso de plásticos {/residuos/plasticos}. No conocemos cómo lo realizas ni las indicaciones de una posible vía de entrega.
```

Campos autorizados: [/residuos/plasticos, /residuos/reciclas]

Marcadores presentes: [/residuos/plasticos]

SHA-256 canónico: `7a29e6adc561acfb24f701d9920ad2b06539c7fc0bb5ac51c57e135dabd53caf`

### SIN_ENFOQUE

Identidad: RE_REVISAR_ENTREGA/1/SIN_ENFOQUE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Consulta las indicaciones para tu entrega
```

Descripción exacta:

```text
Comprueba si tu forma de reciclar incluye entregar materiales. Si es así, consulta qué materiales admite esa vía y cuáles son sus condiciones antes de preparar una entrega.
```

Motivo exacto:

```text
Registraste que reciclas y un uso de plásticos {/residuos/plasticos}. No conocemos cómo lo realizas ni las indicaciones de una posible vía de entrega.
```

Campos autorizados: [/residuos/plasticos, /residuos/reciclas]

Marcadores presentes: [/residuos/plasticos]

SHA-256 canónico: `2b6deec71e28175f669c3e60d710d1e6d7d04277aab9e1473942f4b7221081d9`

## RE_REVISAR_REUTILIZACION / 1

Nombre mostrado: Comprueba un uso de plástico desechable

Categoría: Residuos

Objetivo original:

```text
Identificar oportunidades viables para evitar algún uso desechable.
```

Descripción candidata del catálogo (distinta de las presentaciones):

```text
Revisar si algún uso real de plástico admite una alternativa reutilizable disponible y adecuada, sin imponer compras ni usos inseguros.
```

Condiciones canónicas tipadas (todas deben cumplirse):

```text
{
  "/residuos/plasticos" : [ "Alto", "Bajo", "Medio" ]
}
```

Además: categoría completa y válida, evidencias requeridas y acción no realizada según id y versión.

Evidencias requeridas: [/residuos/plasticos, /residuos/reciclas]

Enfoque 1 y enfoque 2 (orden alfabético): NECESIDAD, REUTILIZACION_VIABLE

Información adicional permitida: [USOS_PLASTICOS_REALES]

Restricciones originales:

```text
Bajo, Medio y Alto describen uso de plásticos, no nivel de reciclaje ni cantidades medidas.
```

```text
No asumir objetos, compras o alternativas disponibles; respetar reciclas y no prometer beneficios cuantificados.
```

### NECESIDAD

Identidad: RE_REVISAR_REUTILIZACION/1/NECESIDAD/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba un uso de plástico desechable
```

Descripción exacta:

```text
Comprueba si entre tus usos de plástico hay alguno desechable. Si identificas uno, comprueba qué necesidad satisface y considera evitarlo solo si esa necesidad queda cubierta sin comprometer la seguridad ni las necesidades esenciales.
```

Motivo exacto:

```text
Registro de reciclaje: {/residuos/reciclas}; uso de plásticos: {/residuos/plasticos}. No conocemos los objetos que utilizas ni las alternativas disponibles.
```

Campos autorizados: [/residuos/plasticos, /residuos/reciclas]

Marcadores presentes: [/residuos/plasticos, /residuos/reciclas]

SHA-256 canónico: `e96055226559c25f0af4cbad9b61913304e7a519862c2f850079900a06a5984a`

### REUTILIZACION_VIABLE

Identidad: RE_REVISAR_REUTILIZACION/1/REUTILIZACION_VIABLE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba un uso de plástico desechable
```

Descripción exacta:

```text
Si encuentras una alternativa reutilizable para un uso real de plástico desechable, comprueba que esté destinada a ese uso y que pueda utilizarse e higienizarse con seguridad. Si no puedes comprobarlo, no la reutilices para ese fin.
```

Motivo exacto:

```text
Registro de reciclaje: {/residuos/reciclas}; uso de plásticos: {/residuos/plasticos}. No conocemos los objetos que utilizas ni las alternativas disponibles.
```

Campos autorizados: [/residuos/plasticos, /residuos/reciclas]

Marcadores presentes: [/residuos/plasticos, /residuos/reciclas]

SHA-256 canónico: `4c4fe16da67464245a0245e33396a2745860bd5a3c2c69f95d04bea008502b09`

### SIN_ENFOQUE

Identidad: RE_REVISAR_REUTILIZACION/1/SIN_ENFOQUE/1

Estado: BORRADOR / PENDIENTE_REVISION / publicable=false

Título exacto:

```text
Comprueba un uso de plástico desechable
```

Descripción exacta:

```text
Comprueba si entre tus usos de plástico hay alguno desechable. Si identificas uno, comprueba si puede evitarse o si encuentras una alternativa reutilizable destinada a ese uso y que pueda utilizarse e higienizarse con seguridad, sin que necesites comprarla. Si no puedes comprobar su adecuación, higiene o seguridad, no la reutilices para ese fin.
```

Motivo exacto:

```text
Registro de reciclaje: {/residuos/reciclas}; uso de plásticos: {/residuos/plasticos}. No conocemos los objetos que utilizas ni las alternativas disponibles.
```

Campos autorizados: [/residuos/plasticos, /residuos/reciclas]

Marcadores presentes: [/residuos/plasticos, /residuos/reciclas]

SHA-256 canónico: `d6de1703932a6f3ee0ecfc3cb01ceae7e3394926db857d14240f6e4fee9bfcc8`

## Variables y procedencia

| Ruta | Procedencia y significado |
|---|---|
| /transporte/medio | Contexto Transporte.medio, cualitativo normalizado desde Habito.unidad |
| /transporte/kmSemana | Transporte.kmSemana, Habito.valor en km/semana |
| /transporte/diasSemana | Transporte.diasSemana, Habito.valor entero de días/semana |
| /energia/vivienda | Energía.vivienda, Habito.unidad cualitativa |
| /energia/personas | Energía.personas, Habito.valor entero |
| /energia/fuente | Energía.fuente, Habito.unidad cualitativa; Electricidad no acredita origen renovable o fósil |
| /energia/consumoKwhDiario | Energía.consumoKwhDiario, opcional, Habito.valor; kWh/día del HOGAR, no por persona |
| /alimentacion/tipo | Alimentación.tipo, Habito.unidad cualitativa |
| /residuos/plasticos | Residuos.plasticos, Habito.unidad cualitativa Bajo/Medio/Alto; no cantidad |
| /residuos/reciclas | Residuos.reciclas, booleano validado desde Habito.valor; en composición se muestra sí/no |

Los marcadores literales conservan el formato {/categoria/campo}. La exportación no recuperó hábitos ni sustituyó valores.
