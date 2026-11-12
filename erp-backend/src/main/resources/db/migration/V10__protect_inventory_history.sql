-- Ordinary DML cannot rewrite recorded movements or finalized inventory documents.
-- Table owners and database administrators can alter/disable triggers; privileges remain essential.
CREATE FUNCTION impedir_cambio_historial_inventario() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'El historial de inventario no se puede modificar' USING ERRCODE = '23514';
END;
$$;

-- Validate the source attributes while locking the referenced document and lines.
-- This does not replace the application transaction that updates stock and state.
CREATE FUNCTION validar_fuente_movimiento_inventario() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NEW.motivo = 'RECEPCION_COMPRA' THEN
        PERFORM 1 FROM detalles_recepcion d
            JOIN recepciones_compra r ON r.id = d.recepcion_id
            JOIN detalles_orden_compra o ON o.id = d.detalle_orden_compra_id AND o.orden_compra_id = r.orden_compra_id
            WHERE d.id = NEW.detalle_recepcion_id AND o.producto_id = NEW.producto_id
              AND r.almacen_id = NEW.almacen_id AND d.cantidad_aceptada = NEW.cantidad
              AND d.precio_unitario_recibido = NEW.costo_unitario
            FOR SHARE OF d, r, o;
    ELSIF NEW.motivo = 'AJUSTE' THEN
        PERFORM 1 FROM detalles_ajuste_inventario d
            JOIN ajustes_inventario a ON a.id = d.ajuste_inventario_id
            WHERE d.id = NEW.detalle_ajuste_id AND d.producto_id = NEW.producto_id
              AND a.almacen_id = NEW.almacen_id AND d.cantidad = NEW.cantidad
              AND ((d.tipo = 'POSITIVO' AND NEW.tipo = 'ENTRADA' AND d.costo_unitario = NEW.costo_unitario)
                   OR (d.tipo = 'NEGATIVO' AND NEW.tipo = 'SALIDA'
                       AND (d.costo_unitario IS NULL OR d.costo_unitario = NEW.costo_unitario)))
            FOR SHARE OF d, a;
    ELSE
        RAISE EXCEPTION 'Fuente de inventario no admitida' USING ERRCODE = '23514';
    END IF;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'El movimiento no coincide con su fuente' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER validar_fuente_movimiento BEFORE INSERT ON movimientos_inventario
    FOR EACH ROW EXECUTE FUNCTION validar_fuente_movimiento_inventario();

CREATE TRIGGER movimientos_inventario_solo_insercion
    BEFORE UPDATE OR DELETE ON movimientos_inventario
    FOR EACH ROW EXECUTE FUNCTION impedir_cambio_historial_inventario();
CREATE TRIGGER movimientos_inventario_sin_truncate
    BEFORE TRUNCATE ON movimientos_inventario
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_cambio_historial_inventario();

CREATE FUNCTION proteger_documento_inventario_final() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (TG_TABLE_NAME = 'conteos_inventario' AND OLD.estado IN ('CERRADO', 'CANCELADO'))
       OR (TG_TABLE_NAME = 'ajustes_inventario' AND OLD.estado = 'APLICADO') THEN
        RAISE EXCEPTION 'El documento de inventario está finalizado' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER proteger_conteo_final BEFORE UPDATE OR DELETE ON conteos_inventario
    FOR EACH ROW EXECUTE FUNCTION proteger_documento_inventario_final();
CREATE TRIGGER proteger_ajuste_aplicado BEFORE UPDATE OR DELETE ON ajustes_inventario
    FOR EACH ROW EXECUTE FUNCTION proteger_documento_inventario_final();

-- Lock both old and new parents in deterministic order. This serializes detail changes
-- with document finalization, and prevents moving a line into or out of a final document.
CREATE FUNCTION proteger_detalle_inventario_final() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE
    anterior bigint;
    siguiente bigint;
    documento record;
BEGIN
    IF TG_TABLE_NAME = 'detalles_conteo' THEN
        IF TG_OP <> 'INSERT' THEN anterior := OLD.conteo_id; END IF;
        IF TG_OP <> 'DELETE' THEN siguiente := NEW.conteo_id; END IF;
        FOR documento IN SELECT id, estado FROM conteos_inventario
                WHERE id = anterior OR id = siguiente ORDER BY id FOR UPDATE LOOP
            IF documento.estado IN ('CERRADO', 'CANCELADO') THEN
                RAISE EXCEPTION 'El conteo está finalizado' USING ERRCODE = '23514';
            END IF;
        END LOOP;
    ELSE
        IF TG_OP <> 'INSERT' THEN anterior := OLD.ajuste_inventario_id; END IF;
        IF TG_OP <> 'DELETE' THEN siguiente := NEW.ajuste_inventario_id; END IF;
        FOR documento IN SELECT id, estado FROM ajustes_inventario
                WHERE id = anterior OR id = siguiente ORDER BY id FOR UPDATE LOOP
            IF documento.estado = 'APLICADO' THEN
                RAISE EXCEPTION 'El ajuste está aplicado' USING ERRCODE = '23514';
            END IF;
        END LOOP;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER proteger_detalle_conteo_final BEFORE INSERT OR UPDATE OR DELETE ON detalles_conteo
    FOR EACH ROW EXECUTE FUNCTION proteger_detalle_inventario_final();
CREATE TRIGGER proteger_detalle_ajuste_aplicado BEFORE INSERT OR UPDATE OR DELETE ON detalles_ajuste_inventario
    FOR EACH ROW EXECUTE FUNCTION proteger_detalle_inventario_final();

CREATE TRIGGER conteos_inventario_sin_truncate BEFORE TRUNCATE ON conteos_inventario
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_cambio_historial_inventario();
CREATE TRIGGER detalles_conteo_sin_truncate BEFORE TRUNCATE ON detalles_conteo
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_cambio_historial_inventario();
CREATE TRIGGER ajustes_inventario_sin_truncate BEFORE TRUNCATE ON ajustes_inventario
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_cambio_historial_inventario();
CREATE TRIGGER detalles_ajuste_inventario_sin_truncate BEFORE TRUNCATE ON detalles_ajuste_inventario
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_cambio_historial_inventario();
