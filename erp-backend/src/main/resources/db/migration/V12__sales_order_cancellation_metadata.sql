-- Preserve existing orders, including legacy cancellations without metadata.
ALTER TABLE pedidos_venta
    ADD COLUMN motivo_cancelacion text,
    ADD COLUMN cancelado_por_usuario_id bigint REFERENCES usuarios(id),
    ADD COLUMN cancelado_en timestamptz;

ALTER TABLE pedidos_venta ADD CONSTRAINT ck_pedido_cancelacion_metadata CHECK (
    (motivo_cancelacion IS NULL AND cancelado_por_usuario_id IS NULL AND cancelado_en IS NULL)
    OR (estado = 'CANCELADO' AND motivo_cancelacion IS NOT NULL AND cancelado_por_usuario_id IS NOT NULL
        AND cancelado_en IS NOT NULL AND motivo_cancelacion = btrim(motivo_cancelacion)
        AND length(motivo_cancelacion) BETWEEN 1 AND 1000)
);

-- Cancellation is final: protect headers and lines from ordinary DML after cancellation.
CREATE FUNCTION proteger_cabecera_cancelada_ventas() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF OLD.estado = 'CANCELADO' THEN
        RAISE EXCEPTION 'El documento cancelado es histórico' USING ERRCODE='23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION proteger_detalle_cancelado_ventas() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE parent_id bigint; parent_state text;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF TG_TABLE_NAME = 'detalles_pedido' THEN parent_id := NEW.pedido_id; ELSE parent_id := NEW.despacho_id; END IF;
    ELSE
        IF TG_TABLE_NAME = 'detalles_pedido' THEN parent_id := OLD.pedido_id; ELSE parent_id := OLD.despacho_id; END IF;
    END IF;
    IF TG_TABLE_NAME = 'detalles_pedido' THEN
        SELECT estado INTO parent_state FROM pedidos_venta WHERE id=parent_id FOR UPDATE;
    ELSE
        SELECT estado INTO parent_state FROM despachos WHERE id=parent_id FOR UPDATE;
    END IF;
    IF parent_state = 'CANCELADO' THEN
        RAISE EXCEPTION 'El detalle pertenece a un documento cancelado' USING ERRCODE='23514';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF TG_TABLE_NAME = 'detalles_pedido' THEN
            IF NEW.pedido_id <> OLD.pedido_id THEN
                SELECT estado INTO parent_state FROM pedidos_venta WHERE id=NEW.pedido_id FOR UPDATE;
            END IF;
        ELSE
            IF NEW.despacho_id <> OLD.despacho_id THEN
                SELECT estado INTO parent_state FROM despachos WHERE id=NEW.despacho_id FOR UPDATE;
            END IF;
        END IF;
        IF parent_state = 'CANCELADO' THEN
            RAISE EXCEPTION 'El detalle pertenece a un documento cancelado' USING ERRCODE='23514';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER proteger_pedido_cancelado BEFORE UPDATE OR DELETE ON pedidos_venta FOR EACH ROW EXECUTE FUNCTION proteger_cabecera_cancelada_ventas();
CREATE TRIGGER proteger_despacho_cancelado BEFORE UPDATE OR DELETE ON despachos FOR EACH ROW EXECUTE FUNCTION proteger_cabecera_cancelada_ventas();
CREATE TRIGGER proteger_linea_pedido_cancelado BEFORE INSERT OR UPDATE OR DELETE ON detalles_pedido FOR EACH ROW EXECUTE FUNCTION proteger_detalle_cancelado_ventas();
CREATE TRIGGER proteger_linea_despacho_cancelado BEFORE INSERT OR UPDATE OR DELETE ON detalles_despacho FOR EACH ROW EXECUTE FUNCTION proteger_detalle_cancelado_ventas();
