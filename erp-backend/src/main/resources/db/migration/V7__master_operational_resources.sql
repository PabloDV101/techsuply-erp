CREATE TABLE proveedores (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    ruc VARCHAR(30) NOT NULL UNIQUE,
    razon_social VARCHAR(160) NOT NULL,
    contacto VARCHAR(100),
    telefono VARCHAR(30),
    email VARCHAR(150),
    direccion TEXT,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE almacenes (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    sede VARCHAR(120),
    ubicacion VARCHAR(200),
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE clientes (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    ruc VARCHAR(30) NOT NULL UNIQUE,
    razon_social VARCHAR(160) NOT NULL,
    contacto VARCHAR(100),
    telefono VARCHAR(30),
    email VARCHAR(150),
    direccion TEXT,
    limite_credito NUMERIC(14,2) NOT NULL DEFAULT 0,
    plazo_pago_dias SMALLINT NOT NULL DEFAULT 30,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_clientes_limite_credito CHECK (limite_credito >= 0),
    CONSTRAINT chk_clientes_plazo_pago CHECK (plazo_pago_dias >= 0)
);

CREATE TABLE productos (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    descripcion VARCHAR(160) NOT NULL,
    unidad_medida VARCHAR(20) NOT NULL,
    categoria_id BIGINT NOT NULL REFERENCES categorias_producto(id),
    precio_referencia NUMERIC(14,2) NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_productos_precio CHECK (precio_referencia >= 0)
);

INSERT INTO permisos(codigo, modulo, accion, descripcion) VALUES
('MAESTROS_PROVEEDORES_LEER', 'MAESTROS', 'PROVEEDORES_LEER', 'Consultar proveedores'),
('PROVEEDOR_WRITE', 'MAESTROS', 'PROVEEDOR_WRITE', 'Crear y actualizar proveedores'),
('MAESTROS_PRODUCTOS_LEER', 'MAESTROS', 'PRODUCTOS_LEER', 'Consultar productos'),
('PRODUCTO_WRITE', 'MAESTROS', 'PRODUCTO_WRITE', 'Crear y actualizar productos'),
('MAESTROS_ALMACENES_LEER', 'MAESTROS', 'ALMACENES_LEER', 'Consultar almacenes'),
('ALMACEN_WRITE', 'MAESTROS', 'ALMACEN_WRITE', 'Crear y actualizar almacenes'),
('MAESTROS_CLIENTES_LEER', 'MAESTROS', 'CLIENTES_LEER', 'Consultar clientes'),
('CLIENTE_WRITE', 'MAESTROS', 'CLIENTE_WRITE', 'Crear y actualizar clientes');

INSERT INTO roles_permisos(rol_id, permiso_id)
SELECT r.id, p.id
FROM roles r
JOIN permisos p ON p.codigo IN (
    'MAESTROS_PROVEEDORES_LEER', 'PROVEEDOR_WRITE',
    'MAESTROS_PRODUCTOS_LEER', 'PRODUCTO_WRITE',
    'MAESTROS_ALMACENES_LEER', 'ALMACEN_WRITE',
    'MAESTROS_CLIENTES_LEER', 'CLIENTE_WRITE'
)
WHERE r.codigo = 'ADMINISTRADOR';
