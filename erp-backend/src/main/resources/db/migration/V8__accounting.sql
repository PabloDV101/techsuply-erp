CREATE TABLE cuentas_contables (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    tipo VARCHAR(15) NOT NULL CHECK (tipo IN ('ACTIVO','PASIVO','CAPITAL','INGRESO','COSTO','GASTO')),
    naturaleza VARCHAR(12) NOT NULL CHECK (naturaleza IN ('DEUDORA','ACREEDORA')),
    nivel SMALLINT NOT NULL DEFAULT 1 CHECK (nivel > 0),
    cuenta_padre_id BIGINT REFERENCES cuentas_contables(id),
    acepta_movimientos BOOLEAN NOT NULL DEFAULT TRUE,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_cuenta_padre CHECK (cuenta_padre_id IS NULL OR cuenta_padre_id <> id)
);

CREATE TABLE periodos_contables (
    id BIGSERIAL PRIMARY KEY,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    estado VARCHAR(10) NOT NULL CHECK (estado IN ('ABIERTO','CERRADO')),
    cerrado_por_usuario_id BIGINT REFERENCES usuarios(id),
    cerrado_en TIMESTAMPTZ,
    CONSTRAINT chk_periodo_fechas CHECK (fecha_fin >= fecha_inicio),
    CONSTRAINT uq_periodo_fechas UNIQUE (fecha_inicio, fecha_fin)
);

CREATE TABLE asientos_contables (
    id BIGSERIAL PRIMARY KEY,
    folio VARCHAR(30) NOT NULL UNIQUE,
    periodo_id BIGINT NOT NULL REFERENCES periodos_contables(id),
    fecha_contable DATE NOT NULL,
    descripcion VARCHAR(300) NOT NULL,
    origen_modulo VARCHAR(30) NOT NULL,
    origen_tipo VARCHAR(40) NOT NULL,
    origen_id BIGINT,
    clave_idempotencia VARCHAR(100),
    contraasiento_de_id BIGINT UNIQUE REFERENCES asientos_contables(id),
    creado_por_usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    contabilizado_por_usuario_id BIGINT REFERENCES usuarios(id),
    estado VARCHAR(12) NOT NULL CHECK (estado IN ('BORRADOR','CONTABILIZADO','REVERSADO')),
    contabilizado_en TIMESTAMPTZ,
    CONSTRAINT uq_asiento_idempotencia UNIQUE (clave_idempotencia)
);

CREATE TABLE partidas_asiento (
    id BIGSERIAL PRIMARY KEY,
    asiento_id BIGINT NOT NULL REFERENCES asientos_contables(id) ON DELETE CASCADE,
    numero_linea SMALLINT NOT NULL,
    cuenta_contable_id BIGINT NOT NULL REFERENCES cuentas_contables(id),
    debe NUMERIC(14,2) NOT NULL DEFAULT 0,
    haber NUMERIC(14,2) NOT NULL DEFAULT 0,
    descripcion VARCHAR(200),
    CONSTRAINT uq_partida_linea UNIQUE (asiento_id, numero_linea),
    CONSTRAINT chk_partida_importes CHECK (debe >= 0 AND haber >= 0),
    CONSTRAINT chk_partida_un_solo_importe CHECK (debe = 0 OR haber = 0)
);

CREATE INDEX idx_asientos_periodo_estado ON asientos_contables(periodo_id, estado);
CREATE INDEX idx_asientos_origen ON asientos_contables(origen_modulo, origen_tipo, origen_id);
CREATE INDEX idx_partidas_cuenta_asiento ON partidas_asiento(cuenta_contable_id, asiento_id);

INSERT INTO permisos(codigo, modulo, accion, descripcion) VALUES
('CONTABILIDAD_LEER', 'CONTABILIDAD', 'LEER', 'Consultar plan de cuentas, periodos y asientos'),
('CONTABILIDAD_ADMIN', 'CONTABILIDAD', 'ADMIN', 'Administrar cuentas, periodos y asientos contables');

INSERT INTO roles_permisos(rol_id, permiso_id)
SELECT r.id, p.id
FROM roles r
JOIN permisos p ON p.codigo IN ('CONTABILIDAD_LEER','CONTABILIDAD_ADMIN')
WHERE r.codigo = 'ADMINISTRADOR';
