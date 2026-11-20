-- Serialize applications for each payment and payable, and preserve their history.
CREATE FUNCTION validar_aplicacion_pago_proveedor() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE pago pagos_proveedor%ROWTYPE;
DECLARE cuenta cuentas_por_pagar%ROWTYPE;
DECLARE aplicado_pago numeric;
DECLARE aplicado_cuenta numeric;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'Las aplicaciones de pagos son inmutables' USING ERRCODE='23514';
    END IF;
    IF NEW.asiento_contable_id IS NOT NULL THEN
        RAISE EXCEPTION 'La aplicación usa el asiento único del pago' USING ERRCODE='23514';
    END IF;
    SELECT * INTO pago FROM pagos_proveedor WHERE id=NEW.pago_proveedor_id FOR UPDATE;
    SELECT * INTO cuenta FROM cuentas_por_pagar WHERE id=NEW.cuenta_por_pagar_id FOR UPDATE;
    IF pago.id IS NULL OR cuenta.id IS NULL OR pago.estado <> 'REGISTRADO'
       OR pago.proveedor_id <> cuenta.proveedor_id THEN
        RAISE EXCEPTION 'La aplicación requiere un pago registrado y una cuenta del mismo proveedor' USING ERRCODE='23514';
    END IF;
    SELECT coalesce(sum(monto_aplicado),0) INTO aplicado_pago FROM aplicaciones_pago_proveedor WHERE pago_proveedor_id=pago.id;
    SELECT coalesce(sum(monto_aplicado),0) INTO aplicado_cuenta FROM aplicaciones_pago_proveedor WHERE cuenta_por_pagar_id=cuenta.id;
    IF aplicado_pago + NEW.monto_aplicado > pago.monto OR aplicado_cuenta + NEW.monto_aplicado > cuenta.monto_original THEN
        RAISE EXCEPTION 'La aplicación excede el disponible o el saldo de la cuenta' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER aplicacion_pago_proveedor_guardia BEFORE INSERT OR UPDATE OR DELETE ON aplicaciones_pago_proveedor
FOR EACH ROW EXECUTE FUNCTION validar_aplicacion_pago_proveedor();

-- Confirmation and all allocations must commit together. This avoids debiting
-- the general ledger payable while leaving an unapplied supplier subledger balance.
CREATE FUNCTION exigir_pago_proveedor_conciliado() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p pagos_proveedor%ROWTYPE;
DECLARE total numeric;
BEGIN
    SELECT * INTO p FROM pagos_proveedor WHERE id=NEW.id;
    IF p.estado = 'CONFIRMADO' THEN
        SELECT coalesce(sum(monto_aplicado),0) INTO total FROM aplicaciones_pago_proveedor WHERE pago_proveedor_id=p.id;
        IF total <> p.monto OR NOT EXISTS (
            SELECT 1 FROM asientos_contables a WHERE a.id=p.asiento_contable_id
              AND a.estado='CONTABILIZADO' AND a.origen_modulo='CONTABILIDAD'
              AND a.origen_tipo='PAGO_PROVEEDOR' AND a.origen_id=p.id
        ) THEN
            RAISE EXCEPTION 'El pago confirmado requiere aplicaciones por su importe y asiento contabilizado' USING ERRCODE='23514';
        END IF;
        IF EXISTS (
          (SELECT origen.cuenta_contable_id, sum(ap.monto_aplicado) AS monto
             FROM aplicaciones_pago_proveedor ap
             JOIN cuentas_por_pagar cxp ON cxp.id=ap.cuenta_por_pagar_id
             JOIN partidas_asiento origen ON origen.asiento_id=cxp.asiento_contable_id
               AND origen.haber=cxp.monto_original AND origen.debe=0
             WHERE ap.pago_proveedor_id=p.id GROUP BY origen.cuenta_contable_id
           EXCEPT
           SELECT cuenta_contable_id, sum(debe) FROM partidas_asiento
             WHERE asiento_id=p.asiento_contable_id AND debe>0 GROUP BY cuenta_contable_id)
          UNION ALL
          (SELECT cuenta_contable_id, sum(debe) FROM partidas_asiento
             WHERE asiento_id=p.asiento_contable_id AND debe>0 GROUP BY cuenta_contable_id
           EXCEPT
           SELECT origen.cuenta_contable_id, sum(ap.monto_aplicado)
             FROM aplicaciones_pago_proveedor ap
             JOIN cuentas_por_pagar cxp ON cxp.id=ap.cuenta_por_pagar_id
             JOIN partidas_asiento origen ON origen.asiento_id=cxp.asiento_contable_id
               AND origen.haber=cxp.monto_original AND origen.debe=0
             WHERE ap.pago_proveedor_id=p.id GROUP BY origen.cuenta_contable_id)
        ) OR (SELECT coalesce(sum(debe),0) FROM partidas_asiento WHERE asiento_id=p.asiento_contable_id) <> p.monto THEN
            RAISE EXCEPTION 'El asiento del pago no concilia con las cuentas originales' USING ERRCODE='23514';
        END IF;
    ELSIF EXISTS (SELECT 1 FROM aplicaciones_pago_proveedor WHERE pago_proveedor_id=p.id) THEN
        RAISE EXCEPTION 'Las aplicaciones requieren confirmar el pago en la misma transacción' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER pago_proveedor_conciliado AFTER INSERT OR UPDATE ON pagos_proveedor
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION exigir_pago_proveedor_conciliado();

CREATE FUNCTION exigir_confirmacion_aplicacion_pago() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pagos_proveedor WHERE id=NEW.pago_proveedor_id AND estado='CONFIRMADO') THEN
        RAISE EXCEPTION 'La aplicación requiere un pago confirmado al finalizar la transacción' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER aplicacion_pago_exige_confirmacion AFTER INSERT ON aplicaciones_pago_proveedor
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION exigir_confirmacion_aplicacion_pago();

CREATE FUNCTION proteger_pago_proveedor() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Los pagos no se eliminan' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.estado <> 'REGISTRADO' THEN
            RAISE EXCEPTION 'El pago debe iniciar registrado' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.estado <> 'REGISTRADO' OR NEW.estado <> 'CONFIRMADO'
       OR (to_jsonb(NEW) - ARRAY['estado','asiento_contable_id','fecha_contable','confirmado_por_usuario_id','confirmado_en'])
          <> (to_jsonb(OLD) - ARRAY['estado','asiento_contable_id','fecha_contable','confirmado_por_usuario_id','confirmado_en']) THEN
        RAISE EXCEPTION 'El pago solo permite su confirmación y conserva su identidad' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER pago_proveedor_historia BEFORE INSERT OR UPDATE OR DELETE ON pagos_proveedor
FOR EACH ROW EXECUTE FUNCTION proteger_pago_proveedor();

-- Preserve the existing lifecycle protections while enabling the payment source.
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
            OR (NEW.origen_modulo='CONTABILIDAD' AND NEW.origen_tipo='PAGO_PROVEEDOR' AND NEW.origen_id IS NOT NULL
                AND EXISTS (SELECT 1 FROM pagos_proveedor WHERE id=NEW.origen_id AND estado='REGISTRADO'))
            OR (NEW.origen_modulo='COMPRAS' AND NEW.origen_tipo='FACTURA_PROVEEDOR' AND NEW.origen_id IS NOT NULL
                AND EXISTS (SELECT 1 FROM facturas_proveedor WHERE id=NEW.origen_id AND estado='REGISTRADA'))
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
        IF OLD.origen_modulo='CONTABILIDAD' AND OLD.origen_tipo='PAGO_PROVEEDOR' THEN
            RAISE EXCEPTION 'La reversión de pagos a proveedores no está habilitada' USING ERRCODE='23514';
        END IF;
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
