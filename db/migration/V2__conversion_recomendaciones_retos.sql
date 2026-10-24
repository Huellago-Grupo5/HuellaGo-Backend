-- HU35. Ejecutar manualmente después de V1, únicamente en la base elegida.
BEGIN;

ALTER TABLE public.retos
    ADD COLUMN IF NOT EXISTS usuario_recomendacion_ia_id integer;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.retos'::regclass
          AND conname = 'fk_retos_usuario_recomendacion_ia'
    ) THEN
        ALTER TABLE public.retos
            ADD CONSTRAINT fk_retos_usuario_recomendacion_ia
            FOREIGN KEY (usuario_recomendacion_ia_id)
            REFERENCES public.usuarios_recomendaciones_ia(id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_retos_usuario_recomendacion_ia
    ON public.retos (usuario_recomendacion_ia_id);

COMMIT;
