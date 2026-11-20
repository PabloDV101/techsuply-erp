-- Durable accounting handoff for issued internal sales invoices.
-- No invoice is auto-posted: the outbox row is created in the invoice transaction;
-- an authorized accounting user configures mappings and retries explicitly.

CREATE TABLE configuracion_cuentas_factura_venta (
    concepto varchar(24) PRIMARY KEY CHECK (concepto IN ('CLIENTES_POR_COBRAR','VENTAS','IMPUESTOS_TRASLADADOS')),
    cuenta_contable_id bigint NOT NULL UNIQUE REFERENCES cuentas_contables(id),
    actualizado_por_usuario_id bigint NOT NULL REFERENCES usuarios(id),
    actualizado_en timestamptz NOT NULL
);

CREATE TABLE integracion_factura_venta_contabilidad (
    factura_venta_id bigint PRIMARY KEY REFERENCES facturas_venta(id),
    estado varchar(16) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','CONTABILIZADA')),
    asiento_contable_id bigint UNIQUE REFERENCES asientos_contables(id),
    fecha_contable date,
    intentos integer NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    ultima_fecha_intentada date,
    ultimo_codigo_error varchar(48),
    ultimo_intento_por_usuario_id bigint REFERENCES usuarios(id),
    emitida_en timestamptz NOT NULL,
    contabilizada_en timestamptz,
    CHECK ((estado = 'PENDIENTE' AND asiento_contable_id IS NULL AND fecha_contable IS NULL AND contabilizada_en IS NULL)
        OR (estado = 'CONTABILIZADA' AND asiento_contable_id IS NOT NULL AND fecha_contable IS NOT NULL AND contabilizada_en IS NOT NULL)),
    CHECK (ultimo_codigo_error IS NULL OR ultimo_codigo_error IN (
        'CONFIGURACION_INCOMPLETA','CUENTAS_INVALIDAS','PERIODO_NO_DISPONIBLE',
        'FACTURA_SIN_IMPORTE','ERROR_CONTABLE'))
);
CREATE INDEX idx_integracion_factura_venta_estado_fecha
    ON integracion_factura_venta_contabilidad(estado, emitida_en, factura_venta_id);

CREATE FUNCTION validar_outbox_factura_venta() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'La integración contable de una factura emitida es histórica' USING ERRCODE='23514';
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NOT EXISTS (SELECT 1 FROM facturas_venta WHERE id=NEW.factura_venta_id AND estado='EMITIDA') THEN
            RAISE EXCEPTION 'Solo se integra una factura emitida' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.factura_venta_id <> OLD.factura_venta_id OR OLD.estado='CONTABILIZADA'
      OR NEW.intentos < OLD.intentos OR NEW.emitida_en <> OLD.emitida_en THEN
        RAISE EXCEPTION 'La integración contable conserva su identidad e historia' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER factura_venta_contabilidad_inmutable
    BEFORE INSERT OR UPDATE OR DELETE ON integracion_factura_venta_contabilidad
    FOR EACH ROW EXECUTE FUNCTION validar_outbox_factura_venta();

-- Permit only the approved invoice source in addition to manual entries.
-- Keep all other V15 lifecycle, period and balance protections unchanged.
CREATE OR REPLACE FUNCTION proteger_transiciones_asiento_contable() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE periodo periodos_contables%ROWTYPE;
DECLARE contra_estado varchar(16);
DECLARE cantidad bigint;
DECLARE total_debe numeric;
DECLARE total_haber numeric;
BEGIN
    PERFORM pg_advisory_xact_lock(141474, 41004);
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Los asientos contables no se eliminan' USING ERRCODE='23514';
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NOT ((NEW.origen_modulo='MANUAL' AND NEW.origen_tipo='MANUAL' AND NEW.origen_id IS NULL)
            OR (NEW.origen_modulo='VENTAS' AND NEW.origen_tipo='FACTURA_VENTA' AND NEW.origen_id IS NOT NULL
                AND EXISTS (SELECT 1 FROM facturas_venta WHERE id=NEW.origen_id AND estado='EMITIDA'))) THEN
            RAISE EXCEPTION 'Origen contable no habilitado' USING ERRCODE='23514';
        END IF;
        SELECT * INTO periodo FROM periodos_contables WHERE id=NEW.periodo_id FOR UPDATE;
        IF NOT FOUND OR periodo.estado <> 'ABIERTO'
          OR NEW.fecha_contable < periodo.fecha_inicio OR NEW.fecha_contable > periodo.fecha_fin THEN
            RAISE EXCEPTION 'El asiento debe usar un periodo abierto que incluya su fecha' USING ERRCODE='23514';
        END IF;
        IF NEW.contraasiento_de_id IS NULL THEN
            IF NEW.estado <> 'BORRADOR' OR NEW.contabilizado_por_usuario_id IS NOT NULL OR NEW.contabilizado_en IS NOT NULL
              OR NEW.motivo_reversion IS NOT NULL THEN
                RAISE EXCEPTION 'Un asiento nuevo debe iniciar como borrador' USING ERRCODE='23514';
            END IF;
        ELSE
            IF NEW.estado <> 'CONTABILIZADO' OR NEW.contabilizado_por_usuario_id IS NULL OR NEW.contabilizado_en IS NULL
              OR NEW.motivo_reversion IS NULL THEN
                RAISE EXCEPTION 'Un contraasiento debe nacer contabilizado con motivo' USING ERRCODE='23514';
            END IF;
            SELECT estado INTO contra_estado FROM asientos_contables WHERE id=NEW.contraasiento_de_id;
            IF contra_estado IS DISTINCT FROM 'CONTABILIZADO' THEN
                RAISE EXCEPTION 'Solo se revierte un asiento contabilizado' USING ERRCODE='23514';
            END IF;
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.estado = 'BORRADOR' THEN
        IF NEW.periodo_id <> OLD.periodo_id OR NEW.folio <> OLD.folio
          OR NEW.origen_modulo <> OLD.origen_modulo OR NEW.origen_tipo <> OLD.origen_tipo
          OR NEW.origen_id IS DISTINCT FROM OLD.origen_id OR NEW.clave_idempotencia <> OLD.clave_idempotencia
          OR NEW.creado_por_usuario_id <> OLD.creado_por_usuario_id OR NEW.contraasiento_de_id IS DISTINCT FROM OLD.contraasiento_de_id
          OR NEW.motivo_reversion IS DISTINCT FROM OLD.motivo_reversion THEN
            RAISE EXCEPTION 'La identidad y el periodo del asiento son inmutables' USING ERRCODE='23514';
        END IF;
        SELECT * INTO periodo FROM periodos_contables WHERE id=NEW.periodo_id FOR UPDATE;
        IF NOT FOUND OR periodo.estado <> 'ABIERTO'
          OR NEW.fecha_contable < periodo.fecha_inicio OR NEW.fecha_contable > periodo.fecha_fin THEN
            RAISE EXCEPTION 'La operación requiere periodo abierto y fecha incluida' USING ERRCODE='23514';
        END IF;
        IF NEW.estado = 'BORRADOR' THEN
            IF NEW.contabilizado_por_usuario_id IS NOT NULL OR NEW.contabilizado_en IS NOT NULL
              OR NEW.cancelado_por_usuario_id IS NOT NULL OR NEW.cancelado_en IS NOT NULL OR NEW.motivo_cancelacion IS NOT NULL THEN
                RAISE EXCEPTION 'Los metadatos de transición no corresponden al borrador' USING ERRCODE='23514';
            END IF;
            RETURN NEW;
        ELSIF NEW.estado = 'CONTABILIZADO' THEN
            IF NEW.contabilizado_por_usuario_id IS NULL OR NEW.contabilizado_en IS NULL
              OR NEW.cancelado_por_usuario_id IS NOT NULL OR NEW.cancelado_en IS NOT NULL OR NEW.motivo_cancelacion IS NOT NULL THEN
                RAISE EXCEPTION 'Metadatos de contabilización inválidos' USING ERRCODE='23514';
            END IF;
            SELECT count(*), coalesce(sum(debe),0), coalesce(sum(haber),0)
              INTO cantidad,total_debe,total_haber FROM partidas_asiento WHERE asiento_id=OLD.id;
            IF cantidad < 2 OR total_debe <> total_haber THEN
                RAISE EXCEPTION 'El asiento requiere al menos dos partidas equilibradas' USING ERRCODE='23514';
            END IF;
            RETURN NEW;
        ELSIF NEW.estado = 'CANCELADO' THEN
            IF NEW.contabilizado_por_usuario_id IS NOT NULL OR NEW.contabilizado_en IS NOT NULL
              OR NEW.cancelado_por_usuario_id IS NULL OR NEW.cancelado_en IS NULL
              OR NEW.motivo_cancelacion IS NULL OR length(btrim(NEW.motivo_cancelacion)) NOT BETWEEN 1 AND 300
              OR NEW.fecha_contable <> OLD.fecha_contable OR NEW.descripcion <> OLD.descripcion THEN
                RAISE EXCEPTION 'La cancelación requiere motivo y no modifica el borrador' USING ERRCODE='23514';
            END IF;
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'Transición de borrador inválida' USING ERRCODE='23514';
    ELSIF OLD.estado='CONTABILIZADO' AND NEW.estado='REVERSADO' THEN
        IF NEW.folio <> OLD.folio OR NEW.periodo_id <> OLD.periodo_id OR NEW.fecha_contable <> OLD.fecha_contable
          OR NEW.descripcion <> OLD.descripcion OR NEW.origen_modulo <> OLD.origen_modulo OR NEW.origen_tipo <> OLD.origen_tipo
          OR NEW.origen_id IS DISTINCT FROM OLD.origen_id OR NEW.clave_idempotencia <> OLD.clave_idempotencia
          OR NEW.contraasiento_de_id IS DISTINCT FROM OLD.contraasiento_de_id
          OR NEW.creado_por_usuario_id <> OLD.creado_por_usuario_id
          OR NEW.contabilizado_por_usuario_id <> OLD.contabilizado_por_usuario_id
          OR NEW.contabilizado_en <> OLD.contabilizado_en THEN
            RAISE EXCEPTION 'El asiento contabilizado conserva su contenido original' USING ERRCODE='23514';
        END IF;
        SELECT estado INTO contra_estado FROM asientos_contables WHERE contraasiento_de_id=OLD.id;
        IF contra_estado IS DISTINCT FROM 'CONTABILIZADO' THEN
            RAISE EXCEPTION 'Se requiere un contraasiento contabilizado' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'La historia contabilizada o cancelada es inmutable' USING ERRCODE='23514';
END;
$$;

INSERT INTO permisos(codigo,modulo,accion,descripcion) VALUES
('CONTABILIDAD_INTEGRACIONES_LEER','CONTABILIDAD','INTEGRACIONES_LEER','Consultar integraciones contables de facturas'),
('CONTABILIDAD_INTEGRACIONES_ADMIN','CONTABILIDAD','INTEGRACIONES_ADMIN','Configurar cuentas de integración contable'),
('CONTABILIDAD_INTEGRACIONES_REINTENTAR','CONTABILIDAD','INTEGRACIONES_REINTENTAR','Reintentar contabilización de facturas');
INSERT INTO roles_permisos(rol_id,permiso_id)
SELECT r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE r.codigo='ADMINISTRADOR' AND p.codigo IN (
  'CONTABILIDAD_INTEGRACIONES_LEER','CONTABILIDAD_INTEGRACIONES_ADMIN','CONTABILIDAD_INTEGRACIONES_REINTENTAR');
