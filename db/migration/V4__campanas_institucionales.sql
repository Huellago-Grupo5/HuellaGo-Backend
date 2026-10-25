-- HU54. Aplicar manualmente tras V1/V2/V3, con respaldo y aplicación detenida.
-- No se crean campañas ni afiliaciones; todos los retos históricos conservan institucion_id NULL.
BEGIN;

ALTER TABLE public.retos ADD COLUMN IF NOT EXISTS institucion_id bigint;

DO $$
DECLARE restriccion record;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.retos'::regclass
                   AND conname = 'fk_retos_institucion') THEN
        ALTER TABLE public.retos ADD CONSTRAINT fk_retos_institucion
            FOREIGN KEY (institucion_id) REFERENCES public.instituciones(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.retos'::regclass
                   AND conname = 'ck_retos_campana_institucional') THEN
        ALTER TABLE public.retos ADD CONSTRAINT ck_retos_campana_institucional CHECK (
            institucion_id IS NULL OR (
                tipo = 'COLECTIVO' AND usuario_recomendacion_ia_id IS NULL
                AND puntos_recompensa IS NOT NULL AND puntos_recompensa = 0
                AND activo IS NOT NULL AND titulo IS NOT NULL AND btrim(titulo) <> ''
                AND descripcion IS NOT NULL AND btrim(descripcion) <> ''
                AND fecha_inicio IS NOT NULL AND fecha_fin IS NOT NULL
                AND fecha_inicio BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'
                AND fecha_fin BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'
                AND fecha_fin >= fecha_inicio
            )
        );
    END IF;

    -- Hibernate pudo generar un CHECK de enum que solo admite ADMIN_INSTITUCIONAL.
    -- Se reemplazan únicamente CHECK de una sola columna (rol), manteniendo las filas.
    FOR restriccion IN
        SELECT c.conname FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attname = 'rol'
        WHERE c.conrelid = 'public.usuarios_instituciones'::regclass AND c.contype = 'c'
          AND array_length(c.conkey, 1) = 1 AND c.conkey[1] = a.attnum
          AND pg_get_constraintdef(c.oid) LIKE '%ADMIN_INSTITUCIONAL%'
    LOOP
        EXECUTE format('ALTER TABLE public.usuarios_instituciones DROP CONSTRAINT %I', restriccion.conname);
    END LOOP;
    ALTER TABLE public.usuarios_instituciones ADD CONSTRAINT ck_usuario_institucion_rol
        CHECK (rol IN ('ADMIN_INSTITUCIONAL', 'MIEMBRO'));
END $$;

CREATE INDEX IF NOT EXISTS ix_retos_institucion_periodo
    ON public.retos (institucion_id, fecha_inicio DESC, id DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_campana_institucion_periodo
    ON public.retos (institucion_id, lower(btrim(titulo)), fecha_inicio, fecha_fin)
    WHERE institucion_id IS NOT NULL;

COMMIT;
