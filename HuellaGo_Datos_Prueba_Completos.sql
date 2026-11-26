-- HuellaGo: datos ficticios integrales para huellagodb_hu29_test.
-- Ejecutar el archivo completo desde Query Tool de pgAdmin. La carga no se ha confirmado;
-- se validó con una copia temporal que termina en ROLLBACK.
-- Clave de todos los usuarios hg.integral.NN@example.invalid: HuellaGoIntegral2026!
-- Este archivo no contiene JWT, sesiones ni inferencias atribuidas a Ollama.
-- Los calculos historicos insertados son escenarios sinteticos, no mediciones.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '180s';
SET LOCAL client_encoding = 'UTF8';

DO $huellago$
DECLARE
    v_n integer;
    v_j integer;
    v_k integer;
    v_uid bigint;
    v_iid bigint;
    v_gid bigint;
    v_rid integer;
    v_urid integer;
    v_recomendacion_id integer;
    v_ids_usuario bigint[] := ARRAY[]::bigint[];
    v_ids_institucion bigint[] := ARRAY[]::bigint[];
    v_ids_grupo bigint[] := ARRAY[]::bigint[];
    v_ids_recomendacion integer[] := ARRAY[]::integer[];
    v_ids_asociacion integer[] := ARRAY[]::integer[];
    v_ids_campana integer[] := ARRAY[]::integer[];
    v_categoria_transporte bigint;
    v_categoria_energia bigint;
    v_categoria_alimentacion bigint;
    v_categoria_residuos bigint;
    v_objetivo_id bigint;
    v_medio text;
    v_vivienda text;
    v_fuente text;
    v_dieta text;
    v_plasticos text;
    v_km numeric;
    v_km_historico numeric;
    v_kwh numeric;
    v_personas integer;
    v_recicla integer;
    v_factor_medio numeric;
    v_factor_vivienda numeric;
    v_factor_fuente numeric;
    v_transporte numeric(38,2);
    v_energia numeric(38,2);
    v_alimentacion numeric(38,2);
    v_residuos numeric(38,2);
    v_factores text := 'transporte.bicicleta=0;transporte.caminando=0;transporte.bus=0.05;transporte.auto=0.12;transporte.moto=0.08;energia.casa=1.2;energia.departamento=0.8;energia.electricidad=1;energia.gas=0.85;energia.solar=0.25;energia.personas=0.25;energia.kwh=0.23825;alimentacion.vegana=0.3;alimentacion.vegetariana=0.5;alimentacion.mixta=1;residuos.bajo=0.2;residuos.medio=0.5;residuos.alto=0.9;residuos.recicla=0.7';
    v_correo text;
    v_nombre_reto text;
    v_nombre_institucion text;
    v_total integer;
    v_hash_prueba text := '$2a$10$LG2eDhPjWKZnURCcK1OkKujLR2U2gxc.msgIYoZWqPGFw/6q4y2A.';
BEGIN
    IF current_database() <> 'huellagodb_hu29_test' THEN
        RAISE EXCEPTION 'Destino no autorizado: %', current_database();
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext('HuellaGo_Datos_Prueba_Completos_v1'));

    -- Repeticion: no insertar ni modificar. Una carga parcial o un conflicto se rechazan.
    SELECT count(*) INTO v_total FROM usuarios u WHERE u.correo LIKE 'hg.integral.%@example.invalid';
    IF v_total <> 0 THEN
        IF v_total <> 30 OR
           (SELECT count(*) FROM instituciones i WHERE i.identificador_fiscal LIKE 'HG-INTEGRAL-%') <> 3 OR
           (SELECT count(*) FROM grupos_institucionales g WHERE g.nombre LIKE 'HG Integral %') <> 6 OR
           (SELECT count(*) FROM usuarios_instituciones ui JOIN usuarios u ON u.id=ui.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid') < 27 OR
           (SELECT count(*) FROM recomendaciones_ia r WHERE r.titulo LIKE 'HG Integral: %') <> 4 OR
           (SELECT count(*) FROM usuarios_recomendaciones_ia ur JOIN usuarios u ON u.id=ur.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid') < 18 THEN
            RAISE EXCEPTION 'Carga integral parcial o marcas en conflicto: revise manualmente; no se insertó nada';
        END IF;
        RAISE NOTICE 'Carga integral ya presente y coherente en sus marcadores principales: cero inserciones';
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM instituciones i WHERE i.identificador_fiscal LIKE 'HG-INTEGRAL-%')
       OR EXISTS (SELECT 1 FROM recomendaciones_ia r WHERE r.titulo LIKE 'HG Integral: %')
       OR EXISTS (SELECT 1 FROM retos r WHERE r.titulo LIKE 'HG Integral: %') THEN
        RAISE EXCEPTION 'Marcas HG Integral reservadas ya ocupadas: cero inserciones';
    END IF;

    SELECT c.id INTO v_categoria_transporte FROM categorias_habito c WHERE c.nombre='Transporte';
    SELECT c.id INTO v_categoria_energia FROM categorias_habito c WHERE c.nombre='Energía';
    SELECT c.id INTO v_categoria_alimentacion FROM categorias_habito c WHERE c.nombre='Alimentación';
    SELECT c.id INTO v_categoria_residuos FROM categorias_habito c WHERE c.nombre='Residuos';
    IF v_categoria_transporte IS NULL OR v_categoria_energia IS NULL OR v_categoria_alimentacion IS NULL OR v_categoria_residuos IS NULL THEN
        RAISE EXCEPTION 'Faltan categorías del catálogo de hábitos';
    END IF;
    IF (SELECT count(*) FROM objetivos_ambientales o WHERE o.nombre IN
        ('Ahorrar energia','Reducir emisiones del transporte','Mejorar mi alimentacion','Reducir y reciclar residuos')) <> 4 THEN
        RAISE EXCEPTION 'Faltan objetivos ambientales existentes';
    END IF;
    IF (SELECT count(*) FROM insignias i WHERE i.nombre IN
        ('Primer paso verde','Eco aprendiz','Guardián verde','Héroe ecológico','Maestro sostenible','Reto de transporte completado','Reto de energía completado')) <> 7 THEN
        RAISE EXCEPTION 'Faltan insignias existentes';
    END IF;

    -- 30 personas ficticias. El nivel sigue la regla actual: eco_puntos / 100 + 1.
    FOR v_n IN 1..30 LOOP
        v_correo := 'hg.integral.' || lpad(v_n::text,2,'0') || '@example.invalid';
        INSERT INTO usuarios AS nuevo_usuario(nombres,apellidos,correo,contrasena,eco_puntos,nivel,
                             notificaciones_activas,visibilidad_comunidad,fecha_creacion,fecha_actualizacion)
        VALUES (CASE WHEN v_n IN (1,11,21) THEN 'Administrador' ELSE 'Usuario' END,
                'Integral ' || lpad(v_n::text,2,'0'),v_correo,v_hash_prueba,
                CASE v_n WHEN 1 THEN 90 WHEN 2 THEN 120 WHEN 3 THEN 50 WHEN 4 THEN 250
                       WHEN 5 THEN 0 WHEN 6 THEN 500 ELSE (v_n % 6)*25 END,
                (CASE v_n WHEN 1 THEN 90 WHEN 2 THEN 120 WHEN 3 THEN 50 WHEN 4 THEN 250
                       WHEN 5 THEN 0 WHEN 6 THEN 500 ELSE (v_n % 6)*25 END)/100+1,
                v_n % 5 <> 0,v_n % 6 <> 0, TIMESTAMP '2026-04-01 10:00:00',TIMESTAMP '2026-04-01 10:00:00')
        RETURNING nuevo_usuario.id INTO v_uid;
        v_ids_usuario := array_append(v_ids_usuario,v_uid);
        SELECT o.id INTO v_objetivo_id FROM objetivos_ambientales o WHERE o.nombre =
            (ARRAY['Ahorrar energia','Reducir emisiones del transporte','Mejorar mi alimentacion','Reducir y reciclar residuos'])[(v_n-1)%4+1];
        INSERT INTO usuarios_objetivos_ambientales(usuario_id,objetivo_id) VALUES(v_uid,v_objetivo_id);
    END LOOP;

    -- Tres instituciones y dos agrupaciones por institución; 9 afiliados cada una.
    FOR v_j IN 1..3 LOOP
        v_nombre_institucion := (ARRAY['Universidad Horizonte Verde','Instituto Eco Andino','Campus Futuro Circular'])[v_j];
        INSERT INTO instituciones AS nueva_institucion(tipo,nombre,pais,identificador_fiscal,correo_contacto,fecha_registro)
        VALUES('UNIVERSIDAD',v_nombre_institucion,'PE','HG-INTEGRAL-'||v_j,
               'institucion'||v_j||'@hg-integral.example.invalid',TIMESTAMP '2026-04-02 09:00:00')
        RETURNING nueva_institucion.id INTO v_iid;
        v_ids_institucion := array_append(v_ids_institucion,v_iid);
        INSERT INTO grupos_institucionales AS nuevo_grupo(institucion_id,tipo,nombre)
        VALUES(v_iid,'EQUIPO','HG Integral Equipo '||v_j) RETURNING nuevo_grupo.id INTO v_gid;
        v_ids_grupo := array_append(v_ids_grupo,v_gid);
        INSERT INTO grupos_institucionales AS nuevo_grupo(institucion_id,tipo,nombre)
        VALUES(v_iid,'FACULTAD','HG Integral Facultad '||v_j) RETURNING nuevo_grupo.id INTO v_gid;
        v_ids_grupo := array_append(v_ids_grupo,v_gid);
        FOR v_k IN 1..9 LOOP
            v_n := (v_j-1)*10+v_k;
            INSERT INTO usuarios_instituciones(usuario_id,institucion_id,rol,grupo_id,fecha_afiliacion)
            VALUES(v_ids_usuario[v_n],v_iid,CASE WHEN v_k=1 THEN 'ADMIN_INSTITUCIONAL' ELSE 'MIEMBRO' END,
                   CASE WHEN v_k<=5 THEN v_ids_grupo[2*v_j-1] ELSE v_ids_grupo[2*v_j] END,
                   TIMESTAMP '2026-04-03 10:00:00');
        END LOOP;
    END LOOP;
    -- Usuarios 10, 20 y 30 quedan sin afiliación para ensayar invitaciones por endpoint.

    -- Hábitos válidos y variados; cada décimo usuario queda deliberadamente incompleto.
    -- No se inventa el campo opcional consumoKwhDiario en fuentes Gas o Solar.
    FOR v_n IN 1..30 LOOP
        v_uid := v_ids_usuario[v_n];
        v_medio := CASE v_n%5 WHEN 0 THEN 'Auto' WHEN 1 THEN 'Auto' WHEN 2 THEN 'Bus'
                         WHEN 3 THEN 'Auto' ELSE 'Moto' END;
        v_km := CASE v_n%5 WHEN 0 THEN 700 WHEN 1 THEN 80 WHEN 2 THEN 160 WHEN 3 THEN 400 ELSE 120 END;
        v_vivienda := CASE WHEN v_n%2=0 THEN 'Casa' ELSE 'Departamento' END;
        v_fuente := CASE v_n%4 WHEN 0 THEN 'Gas' WHEN 1 THEN 'Solar' ELSE 'Electricidad' END;
        v_personas := 1+v_n%4;
        v_kwh := 4+v_n%3;
        v_dieta := CASE v_n%3 WHEN 0 THEN 'Vegana' WHEN 1 THEN 'Vegetariana' ELSE 'Mixta' END;
        v_plasticos := CASE v_n%3 WHEN 0 THEN 'Bajo' WHEN 1 THEN 'Medio' ELSE 'Alto' END;
        v_recicla := CASE WHEN v_n%2=0 THEN 1 ELSE 0 END;
        IF v_n%10=0 THEN
            INSERT INTO habitos(usuario_id,categoria_id,nombre,valor,unidad,fecha_registro,fecha_actualizacion)
            VALUES(v_uid,v_categoria_transporte,'medio',0,v_medio,TIMESTAMP '2026-04-01',TIMESTAMP '2026-04-01');
            CONTINUE;
        END IF;
        INSERT INTO habitos(usuario_id,categoria_id,nombre,valor,unidad,fecha_registro,fecha_actualizacion)
        VALUES
          (v_uid,v_categoria_transporte,'medio',0,v_medio,TIMESTAMP '2026-04-01',TIMESTAMP '2026-04-01'),
          (v_uid,v_categoria_transporte,'kmSemana',v_km,'km/semana',TIMESTAMP '2026-04-01',TIMESTAMP '2026-04-01'),
          (v_uid,v_categoria_transporte,'diasSemana',2+v_n%5,'dias/semana',TIMESTAMP '2026-04-01',TIMESTAMP '2026-04-01'),
          (v_uid,v_categoria_energia,'vivienda',0,v_vivienda,TIMESTAMP '2026-04-01',TIMESTAMP '2026-04-01'),
          (v_uid,v_categoria_energia,'personas',v_personas,'personas',TIMESTAMP '2026-04-01',TIMESTAMP '2026-04-01'),
          (v_uid,v_categoria_energia,'fuente',0,v_fuente,TIMESTAMP '2026-04-01',TIMESTAMP '2026-04-01'),
          (v_uid,v_categoria_alimentacion,'tipo',0,v_dieta,TIMESTAMP '2026-04-01',TIMESTAMP '2026-04-01'),
          (v_uid,v_categoria_residuos,'plasticos',0,v_plasticos,TIMESTAMP '2026-04-01',TIMESTAMP '2026-04-01'),
          (v_uid,v_categoria_residuos,'reciclas',v_recicla,'boolean',TIMESTAMP '2026-04-01',TIMESTAMP '2026-04-01');
        IF v_fuente='Electricidad' THEN
            INSERT INTO habitos(usuario_id,categoria_id,nombre,valor,unidad,fecha_registro,fecha_actualizacion)
            VALUES(v_uid,v_categoria_energia,'consumoKwhDiario',v_kwh,'kwh/dia',TIMESTAMP '2026-04-01',TIMESTAMP '2026-04-01');
        END IF;

        -- Tres huellas semanales sintéticas compatibles por miembro; primera y última
        -- disminuyen en institución 1, aumentan en institución 2 y varían en la 3.
        FOR v_j IN 1..3 LOOP
            v_km_historico := v_km * CASE
                WHEN v_j=1 AND v_n<=10 THEN 1.30
                WHEN v_j=1 AND v_n<=20 THEN 0.70
                WHEN v_j=1 AND v_n%2=0 THEN 1.10
                WHEN v_j=1 THEN 0.90
                WHEN v_j=2 THEN 1.05 ELSE 1 END;
            v_factor_medio := CASE v_medio WHEN 'Auto' THEN 0.12 WHEN 'Bus' THEN 0.05 WHEN 'Moto' THEN 0.08 ELSE 0 END;
            v_factor_vivienda := CASE v_vivienda WHEN 'Casa' THEN 1.2 ELSE 0.8 END;
            v_factor_fuente := CASE v_fuente WHEN 'Gas' THEN 0.85 WHEN 'Solar' THEN 0.25 ELSE 1 END;
            v_transporte := round(v_km_historico*v_factor_medio,2);
            v_energia := CASE WHEN v_fuente='Electricidad' THEN round(v_kwh*7*0.23825,2)
                            ELSE round(v_factor_vivienda*v_factor_fuente*(1+(v_personas-1)*0.25),2) END;
            v_alimentacion := round((CASE v_dieta WHEN 'Vegana' THEN 0.3 WHEN 'Vegetariana' THEN 0.5 ELSE 1 END)*2,2);
            v_residuos := round((CASE v_plasticos WHEN 'Bajo' THEN 0.2 WHEN 'Medio' THEN 0.5 ELSE 0.9 END)
                              *(CASE WHEN v_recicla=1 THEN 0.7 ELSE 1 END)*2,2);
            INSERT INTO huellas_carbono(usuario_id,fecha_calculo,co2_transporte,co2_energia,
                                        co2_alimentacion,co2_residuos,co2_total,
                                        metodologia_version,periodo_calculo,unidad_calculo,perimetro_calculo,factores_aplicados)
            VALUES(v_uid,CASE v_j WHEN 1 THEN TIMESTAMP '2026-05-01 12:00:00'
                              WHEN 2 THEN TIMESTAMP '2026-08-01 12:00:00'
                              ELSE TIMESTAMP '2026-11-20 12:00:00' END,
                   v_transporte,v_energia,v_alimentacion,v_residuos,v_transporte+v_energia+v_alimentacion+v_residuos,
                   'HUELLAGO_SEMANAL_V1','semanal','kg CO2e','PERSONAL_Y_HOGAR',v_factores);
        END LOOP;
    END LOOP;

    -- Cuatro recomendaciones de preparación determinista/histórica. No son salidas de Qwen3.
    -- La asociación no lleva metadatos de Ollama ni identidades editoriales fabricadas.
    FOR v_j IN 1..4 LOOP
        INSERT INTO recomendaciones_ia AS nueva_recomendacion(titulo,descripcion,prioridad,puntos_recompensa,categoria_id,accion_id,version_accion)
        VALUES ((ARRAY['HG Integral: organiza tus desplazamientos','HG Integral: revisa el consumo del hogar',
                       'HG Integral: planifica tu alimentación','HG Integral: separa tus residuos'])[v_j],
                (ARRAY['Revisa tus trayectos habituales y planifica alternativas aplicables.',
                       'Consulta el consumo eléctrico del hogar y detecta usos prescindibles.',
                       'Planifica comidas con los datos de alimentación que registraste.',
                       'Organiza la separación de residuos según tus hábitos registrados.'])[v_j],
                CASE v_j WHEN 1 THEN 'ALTA' WHEN 2 THEN 'MEDIA' ELSE 'BAJA' END,
                0,(ARRAY[v_categoria_transporte,v_categoria_energia,v_categoria_alimentacion,v_categoria_residuos])[v_j],
                NULL,NULL)
        RETURNING nueva_recomendacion.id INTO v_recomendacion_id;
        v_ids_recomendacion := array_append(v_ids_recomendacion,v_recomendacion_id);
    END LOOP;
    FOR v_n IN 1..18 LOOP
        INSERT INTO usuarios_recomendaciones_ia AS nueva_asociacion(usuario_id,recomendacion_ia_id,estado,fecha_generacion,fecha_completado)
        VALUES(v_ids_usuario[v_n],v_ids_recomendacion[(v_n-1)%4+1],
               CASE WHEN v_n<=4 THEN 'completado' ELSE 'Pendiente' END,
               TIMESTAMP '2026-11-21 10:00:00'+v_n*INTERVAL '1 minute',
               CASE WHEN v_n<=4 THEN TIMESTAMP '2026-11-22 10:00:00'+v_n*INTERVAL '1 minute' ELSE NULL END)
        RETURNING nueva_asociacion.id INTO v_urid;
        v_ids_asociacion := array_append(v_ids_asociacion,v_urid);
    END LOOP;

    -- Retos individuales: diario, semanal, disponible futuro y vencido.
    INSERT INTO retos AS nuevo_reto(titulo,descripcion,dificultad,puntos_recompensa,fecha_inicio,fecha_fin,activo,tipo,categoria_id)
    VALUES('HG Integral: reto diario de transporte','Registra una alternativa de transporte hoy.','Fácil',25,CURRENT_DATE,CURRENT_DATE,true,'INDIVIDUAL',v_categoria_transporte)
    RETURNING nuevo_reto.id INTO v_rid;
    INSERT INTO usuarios_retos(usuario_id,reto_id,estado,progreso,fecha_aceptacion,fecha_completado)
    VALUES(v_ids_usuario[1],v_rid,'activo',100,now()-INTERVAL '1 hour',NULL);
    INSERT INTO retos AS nuevo_reto(titulo,descripcion,dificultad,puntos_recompensa,fecha_inicio,fecha_fin,activo,tipo,categoria_id)
    VALUES('HG Integral: reto semanal de energía','Revisa los hábitos de consumo eléctrico del hogar esta semana.','Media',20,
           CURRENT_DATE-2,CURRENT_DATE+4,true,'INDIVIDUAL',v_categoria_energia)
    RETURNING nuevo_reto.id INTO v_rid;
    INSERT INTO usuarios_retos(usuario_id,reto_id,estado,progreso,fecha_aceptacion,fecha_completado)
    VALUES(v_ids_usuario[2],v_rid,'completado',100,now()-INTERVAL '3 days',now()-INTERVAL '1 day');
    INSERT INTO retos(titulo,descripcion,dificultad,puntos_recompensa,fecha_inicio,fecha_fin,activo,tipo,categoria_id)
    VALUES('HG Integral: reto futuro de residuos','Prepara la separación de residuos de la próxima semana.','Fácil',10,
           CURRENT_DATE+7,CURRENT_DATE+14,true,'INDIVIDUAL',v_categoria_residuos);
    INSERT INTO retos(titulo,descripcion,dificultad,puntos_recompensa,fecha_inicio,fecha_fin,activo,tipo,categoria_id)
    VALUES('HG Integral: reto vencido de alimentación','Actividad sintética cerrada del mes anterior.','Media',10,
           CURRENT_DATE-30,CURRENT_DATE-23,false,'INDIVIDUAL',v_categoria_alimentacion);

    -- Dos conversiones históricas sintéticas desde asociaciones propias, sin tocar otras.
    FOR v_j IN 1..2 LOOP
        v_n := v_j+4;
        INSERT INTO retos AS nuevo_reto(titulo,descripcion,dificultad,puntos_recompensa,fecha_inicio,fecha_fin,activo,tipo,categoria_id,usuario_recomendacion_ia_id)
        SELECT 'HG Integral: reto personalizado '||v_j,r.descripcion,'Media',0,CURRENT_DATE,CURRENT_DATE+7,true,
               'INDIVIDUAL',r.categoria_id,v_ids_asociacion[v_n]
        FROM recomendaciones_ia r WHERE r.id=v_ids_recomendacion[(v_n-1)%4+1]
        RETURNING nuevo_reto.id INTO v_rid;
        INSERT INTO usuarios_retos(usuario_id,reto_id,estado,progreso,fecha_aceptacion,fecha_completado)
        VALUES(v_ids_usuario[v_n],v_rid,'activo',v_j*25,now()-INTERVAL '2 hours',NULL);
    END LOOP;

    -- Campañas por institución: finalizada, activa y futura.
    FOR v_j IN 1..3 LOOP
        FOR v_k IN 1..3 LOOP
            v_nombre_reto := 'HG Integral: campaña '||v_j||'-'||v_k;
            INSERT INTO retos AS nuevo_reto(titulo,descripcion,dificultad,puntos_recompensa,fecha_inicio,fecha_fin,
                              activo,tipo,categoria_id,institucion_id)
            VALUES(v_nombre_reto,'Campaña colectiva ficticia de la institución '||v_j||'.',
                   'Media',0,
                   CASE v_k WHEN 1 THEN CURRENT_DATE-45 WHEN 2 THEN CURRENT_DATE-5 ELSE CURRENT_DATE+10 END,
                   CASE v_k WHEN 1 THEN CURRENT_DATE-15 WHEN 2 THEN CURRENT_DATE+10 ELSE CURRENT_DATE+30 END,
                   v_k<>1,'COLECTIVO',v_categoria_residuos,v_ids_institucion[v_j])
            RETURNING nuevo_reto.id INTO v_rid;
            v_ids_campana := array_append(v_ids_campana,v_rid);
            IF v_k<=2 THEN
                FOR v_n IN ((v_j-1)*10+1)..((v_j-1)*10+3) LOOP
                    INSERT INTO usuarios_retos(usuario_id,reto_id,estado,progreso,fecha_aceptacion,fecha_completado)
                    VALUES(v_ids_usuario[v_n],v_rid,
                           CASE WHEN v_k=1 THEN 'completado' ELSE 'activo' END,
                           CASE WHEN v_k=1 THEN 100 ELSE (v_n%3+1)*20 END,
                           CASE WHEN v_k=1 THEN now()-INTERVAL '40 days' ELSE now()-INTERVAL '4 days' END,
                           CASE WHEN v_k=1 THEN now()-INTERVAL '20 days' ELSE NULL END);
                END LOOP;
            END IF;
            IF v_k=1 THEN
                INSERT INTO resultados_retos_colectivos(reto_id,estado,fecha_cierre,participantes,progreso_final)
                VALUES(v_rid,'EXITOSO',now()-INTERVAL '15 days',3,100);
            END IF;
        END LOOP;
    END LOOP;

    -- Insignias acumuladas coherentes con eco puntos y con un reto completado.
    INSERT INTO usuarios_insignias(usuario_id,insignia_id,fecha_obtencion)
    SELECT u.id,i.id,TIMESTAMP '2026-11-23 12:00:00'
    FROM usuarios u JOIN insignias i ON i.puntos_requeridos IS NOT NULL
    WHERE u.correo LIKE 'hg.integral.%@example.invalid' AND u.eco_puntos>=i.puntos_requeridos
      AND i.nombre IN ('Primer paso verde','Eco aprendiz','Guardián verde','Héroe ecológico','Maestro sostenible');
    INSERT INTO usuarios_insignias(usuario_id,insignia_id,fecha_obtencion)
    SELECT v_ids_usuario[2],i.id,TIMESTAMP '2026-11-24 12:00:00'
    FROM insignias i WHERE i.nombre='Reto de energía completado';

    -- Eventos internos de prueba solo para usuarios que conservan la preferencia activa.
    INSERT INTO notificaciones(usuario_id,titulo,mensaje,tipo,leida,fecha_creacion,clave_evento)
    SELECT ur.usuario_id,'Reto completado','Completaste un reto de prueba integral.',
           'RETO_COMPLETADO',false,ur.fecha_completado,'RETO_COMPLETADO:'||ur.id
    FROM usuarios_retos ur JOIN usuarios u ON u.id=ur.usuario_id
    JOIN retos r ON r.id=ur.reto_id
    WHERE u.correo LIKE 'hg.integral.%@example.invalid' AND u.notificaciones_activas
      AND ur.estado='completado' AND r.institucion_id IS NULL;
    INSERT INTO notificaciones(usuario_id,titulo,mensaje,tipo,leida,fecha_creacion,clave_evento)
    SELECT ui.usuario_id,'Insignia obtenida','Obtuviste una insignia de prueba integral.',
           'INSIGNIA',ui.id%2=0,ui.fecha_obtencion,'INSIGNIA:'||ui.insignia_id
    FROM usuarios_insignias ui JOIN usuarios u ON u.id=ui.usuario_id
    WHERE u.correo LIKE 'hg.integral.%@example.invalid' AND u.notificaciones_activas;
    INSERT INTO notificaciones(usuario_id,titulo,mensaje,tipo,leida,fecha_creacion,clave_evento)
    SELECT u.id,'Nuevo nivel','Alcanzaste el nivel '||u.nivel||' en este escenario sintético.',
           'NIVEL',true,TIMESTAMP '2026-11-23 12:00:00','NIVEL_ALCANZADO:'||u.nivel
    FROM usuarios u WHERE u.correo LIKE 'hg.integral.%@example.invalid'
      AND u.notificaciones_activas AND u.nivel>1;

    -- Publicaciones con único origen permitido: insignia o participación propia.
    INSERT INTO publicaciones_comunitarias(autor_id,insignia_id,usuario_reto_id,titulo,fecha_publicacion)
    SELECT ui.usuario_id,ui.insignia_id,NULL,'HG Integral: compartí una insignia '||ui.insignia_id,
           TIMESTAMP '2026-11-24 15:00:00'
    FROM usuarios_insignias ui JOIN usuarios u ON u.id=ui.usuario_id
    WHERE u.id IN (v_ids_usuario[1],v_ids_usuario[2])
      AND ui.insignia_id=(SELECT i.id FROM insignias i WHERE i.nombre='Primer paso verde');
    INSERT INTO publicaciones_comunitarias(autor_id,insignia_id,usuario_reto_id,titulo,fecha_publicacion)
    SELECT ur.usuario_id,NULL,ur.id,'HG Integral: compartí un reto completado',
           TIMESTAMP '2026-11-24 15:15:00'
    FROM usuarios_retos ur JOIN usuarios u ON u.id=ur.usuario_id
    JOIN retos r ON r.id=ur.reto_id
    WHERE u.id=v_ids_usuario[2] AND ur.estado='completado' AND r.institucion_id IS NULL;

    -- Aserciones finales: ante una anomalía, PostgreSQL revierte todo el bloque.
    IF (SELECT count(*) FROM usuarios u WHERE u.correo LIKE 'hg.integral.%@example.invalid')<>30
       OR (SELECT count(*) FROM instituciones i WHERE i.identificador_fiscal LIKE 'HG-INTEGRAL-%')<>3
       OR (SELECT count(*) FROM grupos_institucionales g WHERE g.nombre LIKE 'HG Integral %')<>6
       OR (SELECT count(*) FROM usuarios_instituciones ui JOIN usuarios u ON u.id=ui.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid')<>27
       OR (SELECT count(*) FROM huellas_carbono h JOIN usuarios u ON u.id=h.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid')<>81
       OR (SELECT count(*) FROM recomendaciones_ia r WHERE r.titulo LIKE 'HG Integral: %')<>4
       OR (SELECT count(*) FROM usuarios_recomendaciones_ia ur JOIN usuarios u ON u.id=ur.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid')<>18
       OR (SELECT count(*) FROM retos r WHERE r.titulo LIKE 'HG Integral: %')<>15
       OR (SELECT count(*) FROM resultados_retos_colectivos rc JOIN retos r ON r.id=rc.reto_id WHERE r.titulo LIKE 'HG Integral: %')<>3
       OR EXISTS (SELECT 1 FROM usuarios u WHERE u.correo LIKE 'hg.integral.%@example.invalid' AND u.nivel<>u.eco_puntos/100+1)
       OR EXISTS (SELECT 1 FROM huellas_carbono h JOIN usuarios u ON u.id=h.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid' AND h.co2_total<>h.co2_transporte+h.co2_energia+h.co2_alimentacion+h.co2_residuos)
       OR EXISTS (SELECT 1 FROM usuarios_instituciones ui JOIN grupos_institucionales g ON g.id=ui.grupo_id JOIN usuarios u ON u.id=ui.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid' AND g.institucion_id<>ui.institucion_id)
    THEN
        RAISE EXCEPTION 'Verificación integral fallida: toda la transacción será revertida';
    END IF;
    RAISE NOTICE 'HuellaGo integral: 30 usuarios, 3 instituciones, 6 grupos, 27 afiliaciones, 81 huellas, 4 canónicas, 18 asociaciones, 15 retos y 3 resultados colectivos preparados';
END
$huellago$;

-- Consulta informativa dentro de la misma transacción; el COMMIT solo se alcanza
-- si el bloque anterior y todas sus aserciones terminaron sin error.
SELECT 'usuarios' AS tabla,count(*) AS registros FROM usuarios u WHERE u.correo LIKE 'hg.integral.%@example.invalid'
UNION ALL SELECT 'instituciones',count(*) FROM instituciones i WHERE i.identificador_fiscal LIKE 'HG-INTEGRAL-%'
UNION ALL SELECT 'grupos_institucionales',count(*) FROM grupos_institucionales g WHERE g.nombre LIKE 'HG Integral %'
UNION ALL SELECT 'habitos',count(*) FROM habitos h JOIN usuarios u ON u.id=h.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid'
UNION ALL SELECT 'huellas_carbono',count(*) FROM huellas_carbono h JOIN usuarios u ON u.id=h.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid'
UNION ALL SELECT 'retos',count(*) FROM retos r WHERE r.titulo LIKE 'HG Integral: %'
UNION ALL SELECT 'usuarios_retos',count(*) FROM usuarios_retos ur JOIN usuarios u ON u.id=ur.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid'
UNION ALL SELECT 'recomendaciones_ia',count(*) FROM recomendaciones_ia r WHERE r.titulo LIKE 'HG Integral: %'
UNION ALL SELECT 'usuarios_recomendaciones_ia',count(*) FROM usuarios_recomendaciones_ia ur JOIN usuarios u ON u.id=ur.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid'
UNION ALL SELECT 'notificaciones',count(*) FROM notificaciones nt JOIN usuarios u ON u.id=nt.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid'
UNION ALL SELECT 'publicaciones_comunitarias',count(*) FROM publicaciones_comunitarias p WHERE p.titulo LIKE 'HG Integral: %'
UNION ALL SELECT 'usuarios_insignias',count(*) FROM usuarios_insignias ui JOIN usuarios u ON u.id=ui.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid'
UNION ALL SELECT 'usuarios_objetivos_ambientales',count(*) FROM usuarios_objetivos_ambientales x JOIN usuarios u ON u.id=x.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid'
UNION ALL SELECT 'resultados_retos_colectivos',count(*) FROM resultados_retos_colectivos x JOIN retos r ON r.id=x.reto_id WHERE r.titulo LIKE 'HG Integral: %'
UNION ALL SELECT 'usuarios_instituciones',count(*) FROM usuarios_instituciones ui JOIN usuarios u ON u.id=ui.usuario_id WHERE u.correo LIKE 'hg.integral.%@example.invalid'
ORDER BY tabla;
COMMIT;
