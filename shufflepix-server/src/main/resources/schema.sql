-- Схема бази даних shufflepix_db (PostgreSQL 16).
-- Виконується серверним застосунком під час запуску
-- (spring.sql.init.mode=always), повторне виконання безпечне.
CREATE TABLE IF NOT EXISTS projects (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    width            INTEGER      NOT NULL CHECK (width BETWEEN 1 AND 8192),
    height           INTEGER      NOT NULL CHECK (height BETWEEN 1 AND 8192),
    background_color CHAR(9)      NOT NULL DEFAULT '#FFFFFFFF'
                     CHECK (background_color ~ '^#[0-9A-F]{8}$'),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_projects_dates CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_projects_name
    ON projects (lower(name));

CREATE INDEX IF NOT EXISTS ix_projects_updated_at
    ON projects (updated_at DESC);

CREATE TABLE IF NOT EXISTS layers (
    id          UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID             NOT NULL
                REFERENCES projects (id) ON DELETE CASCADE,
    name        VARCHAR(100)     NOT NULL CHECK (btrim(name) <> ''),
    position    INTEGER          NOT NULL CHECK (position >= 0),
    visible     BOOLEAN          NOT NULL DEFAULT TRUE,
    opacity     DOUBLE PRECISION NOT NULL DEFAULT 1
                CHECK (opacity BETWEEN 0 AND 1),
    CONSTRAINT ux_layers_project_pos UNIQUE (project_id, position)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE IF NOT EXISTS layer_contents (
    layer_id    UUID        PRIMARY KEY
                REFERENCES layers (id) ON DELETE CASCADE,
    image_png   BYTEA       NOT NULL
                CHECK (octet_length(image_png) BETWEEN 1 AND 20971520),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE layer_contents ALTER COLUMN image_png SET STORAGE EXTERNAL;

CREATE TABLE IF NOT EXISTS effect_types (
    code               VARCHAR(20) PRIMARY KEY CHECK (code ~ '^[A-Z_]+$'),
    name               VARCHAR(50) NOT NULL UNIQUE,
    default_parameters JSONB       NOT NULL DEFAULT '{}'
                       CHECK (jsonb_typeof(default_parameters) = 'object')
);

CREATE TABLE IF NOT EXISTS layer_effects (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    layer_id     UUID        NOT NULL
                 REFERENCES layers (id) ON DELETE CASCADE,
    position     INTEGER     NOT NULL CHECK (position >= 0),
    effect_code  VARCHAR(20) NOT NULL
                 REFERENCES effect_types (code) ON DELETE RESTRICT,
    parameters   JSONB       NOT NULL DEFAULT '{}'
                 CHECK (jsonb_typeof(parameters) = 'object'),
    CONSTRAINT ux_effects_layer_pos UNIQUE (layer_id, position)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE IF NOT EXISTS project_thumbnails (
    project_id  UUID        PRIMARY KEY
                REFERENCES projects (id) ON DELETE CASCADE,
    image_png   BYTEA       NOT NULL,
    width       INTEGER     NOT NULL CHECK (width BETWEEN 1 AND 256),
    height      INTEGER     NOT NULL CHECK (height BETWEEN 1 AND 256),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE project_thumbnails ALTER COLUMN image_png SET STORAGE EXTERNAL;

INSERT INTO effect_types (code, name, default_parameters) VALUES
    ('GRAYSCALE', 'Чорно-білий',   '{}'),
    ('INFRARED',  'Інфрачервоний', '{}'),
    ('NEGATIVE',  'Негатив',       '{}'),
    ('SEPIA',     'Сепія',         '{}'),
    ('BLUR',      'Розмиття',      '{"radius": 5}')
ON CONFLICT (code) DO NOTHING;
