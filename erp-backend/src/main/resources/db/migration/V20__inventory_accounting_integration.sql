-- Inventory and receipt accounting is synchronous with the source transaction.
-- A missing account or open period therefore rolls back the operational movement.
CREATE TABLE configuracion_cuentas_inventario (
    concepto varchar(50) PRIMARY KEY CHECK (concepto IN (
        'INVENTARIO', 'MERCANCIA_RECIBIDA_NO_FACTURADA', 'COSTO_DE_VENTAS',
        'AJUSTE_INVENTARIO_POSITIVO', 'AJUSTE_INVENTARIO_NEGATIVO')),
    cuenta_contable_id bigint NOT NULL REFERENCES cuentas_contables(id),
    actualizado_por_usuario_id bigint NOT NULL REFERENCES usuarios(id),
    actualizado_en timestamptz NOT NULL
);

INSERT INTO permisos(codigo, modulo, accion, descripcion) VALUES
 ('CONTABILIDAD_INVENTARIO_CONFIGURAR','CONTABILIDAD','INVENTARIO_CONFIGURAR','Configurar cuentas para hechos de Inventario y recepción'),
 ('CONTABILIDAD_INVENTARIO_LEER','CONTABILIDAD','INVENTARIO_LEER','Consultar configuración contable de Inventario');
INSERT INTO roles_permisos(rol_id,permiso_id)
SELECT r.id,p.id FROM roles r CROSS JOIN permisos p WHERE r.codigo='ADMINISTRADOR'
 AND p.codigo IN ('CONTABILIDAD_INVENTARIO_CONFIGURAR','CONTABILIDAD_INVENTARIO_LEER');

-- Extend the existing origin gate; retain all period, lifecycle and balance checks.
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
                AND EXISTS (SELECT 1 FROM facturas_venta WHERE id=NEW.origen_id AND estado='EMITIDA'))
            OR (NEW.origen_modulo='CONTABILIDAD' AND NEW.origen_tipo IN ('COBRO_CLIENTE','APLICACION_COBRO') AND NEW.origen_id IS NOT NULL
                AND ((NEW.origen_tipo='COBRO_CLIENTE' AND EXISTS (SELECT 1 FROM cobros_clientes WHERE id=NEW.origen_id AND estado IN ('REGISTRADO','CONFIRMADO')))
                  OR (NEW.origen_tipo='APLICACION_COBRO' AND EXISTS (SELECT 1 FROM aplicaciones_cobro WHERE id=NEW.origen_id))))
            OR (NEW.origen_modulo='COMPRAS' AND NEW.origen_tipo='RECEPCION_COMPRA' AND NEW.origen_id IS NOT NULL
                AND EXISTS (SELECT 1 FROM recepciones_compra WHERE id=NEW.origen_id AND estado='CONFIRMADA'))
            OR (NEW.origen_modulo='VENTAS' AND NEW.origen_tipo='DESPACHO_VENTA' AND NEW.origen_id IS NOT NULL
                AND EXISTS (SELECT 1 FROM despachos WHERE id=NEW.origen_id AND estado='CONFIRMADO'))
            OR (NEW.origen_modulo='INVENTARIO' AND NEW.origen_tipo='AJUSTE_INVENTARIO' AND NEW.origen_id IS NOT NULL
                AND EXISTS (SELECT 1 FROM ajustes_inventario WHERE id=NEW.origen_id AND estado='APLICADO'))) THEN
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

CREATE FUNCTION exigir_asiento_operativo_contabilizado() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source_type text;
DECLARE source_status text;
DECLARE source_module text;
DECLARE requires_entry boolean := false;
BEGIN
    IF TG_TABLE_NAME='recepciones_compra' THEN
        source_type := 'RECEPCION_COMPRA'; source_status := 'CONFIRMADA'; source_module := 'COMPRAS';
        SELECT coalesce(sum(d.cantidad_aceptada*d.precio_unitario_recibido),0)<>0 INTO requires_entry
          FROM detalles_recepcion d WHERE d.recepcion_id=NEW.id;
    ELSIF TG_TABLE_NAME='despachos' THEN
        source_type := 'DESPACHO_VENTA'; source_status := 'CONFIRMADO'; source_module := 'VENTAS';
        SELECT coalesce(sum(m.cantidad*m.costo_unitario),0)<>0 INTO requires_entry
          FROM movimientos_inventario m JOIN detalles_despacho d ON d.id=m.detalle_despacho_id WHERE d.despacho_id=NEW.id;
    ELSE
        source_type := 'AJUSTE_INVENTARIO'; source_status := 'APLICADO'; source_module := 'INVENTARIO';
        SELECT coalesce(sum(m.cantidad*m.costo_unitario),0)<>0 INTO requires_entry
          FROM movimientos_inventario m JOIN detalles_ajuste_inventario d ON d.id=m.detalle_ajuste_id WHERE d.ajuste_inventario_id=NEW.id;
    END IF;
    IF NEW.estado=source_status AND (TG_OP='INSERT' OR OLD.estado IS DISTINCT FROM NEW.estado)
       AND requires_entry AND NOT EXISTS (SELECT 1 FROM asientos_contables a WHERE a.origen_modulo=source_module
            AND a.origen_tipo=source_type AND a.origen_id=NEW.id AND a.estado='CONTABILIZADO') THEN
        RAISE EXCEPTION 'La operación requiere su asiento contable dentro de la transacción' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER recepcion_exige_asiento_contable
AFTER UPDATE ON recepciones_compra DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION exigir_asiento_operativo_contabilizado();
CREATE CONSTRAINT TRIGGER despacho_exige_asiento_contable
AFTER UPDATE ON despachos DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION exigir_asiento_operativo_contabilizado();
CREATE CONSTRAINT TRIGGER ajuste_exige_asiento_contable
AFTER UPDATE ON ajustes_inventario DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION exigir_asiento_operativo_contabilizado();
