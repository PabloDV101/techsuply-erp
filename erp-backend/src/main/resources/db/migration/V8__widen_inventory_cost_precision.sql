-- Preserve existing values while allowing every numeric(14,2) receipt price
-- to be represented in inventory with four decimal places.
ALTER TABLE existencias ALTER COLUMN costo_promedio TYPE numeric(16,4);
ALTER TABLE movimientos_inventario ALTER COLUMN costo_unitario TYPE numeric(16,4);
