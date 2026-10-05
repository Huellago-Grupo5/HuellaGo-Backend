INSERT INTO categorias_habito (nombre, descripcion)
SELECT 'Transporte', 'Hábitos relacionados con medios de transporte y movilidad'
WHERE NOT EXISTS (SELECT 1 FROM categorias_habito
                  WHERE LOWER(translate(nombre, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = LOWER('Transporte'));

INSERT INTO categorias_habito (nombre, descripcion)
SELECT 'Energía', 'Hábitos relacionados con el consumo de energía'
WHERE NOT EXISTS (SELECT 1 FROM categorias_habito
                  WHERE LOWER(translate(nombre, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = LOWER(translate('Energía', 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')));

INSERT INTO categorias_habito (nombre, descripcion)
SELECT 'Alimentación', 'Hábitos relacionados con alimentación'
WHERE NOT EXISTS (SELECT 1 FROM categorias_habito
                  WHERE LOWER(translate(nombre, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = LOWER(translate('Alimentación', 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')));

INSERT INTO categorias_habito (nombre, descripcion)
SELECT 'Residuos', 'Hábitos relacionados con generación y manejo de residuos'
WHERE NOT EXISTS (SELECT 1 FROM categorias_habito
                  WHERE LOWER(translate(nombre, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) = LOWER('Residuos'));

INSERT INTO insignias (nombre, descripcion, imagen_url, puntos_requeridos)
SELECT 'Primer paso verde', 'Comenzaste a sumar eco-puntos con acciones sostenibles.', '/img/primer-paso-verde.png', 10
WHERE NOT EXISTS (SELECT 1 FROM insignias WHERE nombre = 'Primer paso verde');

INSERT INTO insignias (nombre, descripcion, imagen_url, puntos_requeridos)
SELECT 'Eco aprendiz', 'Alcanzaste 50 eco-puntos con tus acciones sostenibles.', '/img/eco-aprendiz.png', 50
WHERE NOT EXISTS (SELECT 1 FROM insignias WHERE nombre = 'Eco aprendiz');

INSERT INTO insignias (nombre, descripcion, imagen_url, puntos_requeridos)
SELECT 'Guardián verde', 'Alcanzaste 100 eco-puntos y demostraste tu compromiso ambiental.', '/img/guardian-verde.png', 100
WHERE NOT EXISTS (SELECT 1 FROM insignias WHERE nombre = 'Guardián verde');

INSERT INTO insignias (nombre, descripcion, imagen_url, puntos_requeridos)
SELECT 'Héroe ecológico', 'Alcanzaste 250 eco-puntos.', '/img/heroe-ecologico.png', 250
WHERE NOT EXISTS (SELECT 1 FROM insignias WHERE nombre = 'Héroe ecológico');

INSERT INTO insignias (nombre, descripcion, imagen_url, puntos_requeridos)
SELECT 'Maestro sostenible', 'Alcanzaste 500 eco-puntos.', '/img/maestro-sostenible.png', 500
WHERE NOT EXISTS (SELECT 1 FROM insignias WHERE nombre = 'Maestro sostenible');

INSERT INTO retos (titulo, descripcion, dificultad, fecha_inicio, fecha_fin, puntos_recompensa, activo, categoria_id)
SELECT 'Reto transporte sostenible', 'Usa bicicleta o camina durante una semana para reducir tu huella de carbono.',
       'Media', DATE '2026-10-04', DATE '2026-10-11', 100, TRUE, c.id
FROM categorias_habito c
WHERE LOWER(c.nombre) = LOWER('Transporte')
  AND NOT EXISTS (SELECT 1 FROM retos WHERE titulo = 'Reto transporte sostenible');

INSERT INTO retos (titulo, descripcion, dificultad, fecha_inicio, fecha_fin, puntos_recompensa, activo, categoria_id)
SELECT 'Reto ahorro de energía', 'Reduce el consumo de energía durante una semana.',
       'Media', DATE '2026-10-04', DATE '2026-10-18', 150, TRUE, c.id
FROM categorias_habito c
WHERE LOWER(c.nombre) = LOWER('Energía')
  AND NOT EXISTS (SELECT 1 FROM retos WHERE titulo = 'Reto ahorro de energía');
