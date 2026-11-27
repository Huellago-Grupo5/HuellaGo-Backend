-- Additive user display preferences. Existing accounts receive the original defaults.
ALTER TABLE public.usuarios
    ADD COLUMN IF NOT EXISTS tema_preferido varchar(10) NOT NULL DEFAULT 'claro';

ALTER TABLE public.usuarios
    ADD COLUMN IF NOT EXISTS unidades_preferidas varchar(10) NOT NULL DEFAULT 'metric';

UPDATE public.usuarios SET tema_preferido = 'claro' WHERE tema_preferido IS NULL;
UPDATE public.usuarios SET unidades_preferidas = 'metric' WHERE unidades_preferidas IS NULL;
ALTER TABLE public.usuarios ALTER COLUMN tema_preferido SET DEFAULT 'claro';
ALTER TABLE public.usuarios ALTER COLUMN tema_preferido SET NOT NULL;
ALTER TABLE public.usuarios ALTER COLUMN unidades_preferidas SET DEFAULT 'metric';
ALTER TABLE public.usuarios ALTER COLUMN unidades_preferidas SET NOT NULL;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.usuarios'::regclass
                   AND conname='ck_usuarios_tema_preferido') THEN
        ALTER TABLE public.usuarios ADD CONSTRAINT ck_usuarios_tema_preferido
            CHECK (tema_preferido IN ('claro', 'oscuro'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.usuarios'::regclass
                   AND conname='ck_usuarios_unidades_preferidas') THEN
        ALTER TABLE public.usuarios ADD CONSTRAINT ck_usuarios_unidades_preferidas
            CHECK (unidades_preferidas IN ('metric', 'imperial'));
    END IF;
END $$;
