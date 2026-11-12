package com.techsupply.erp_backend.ventas.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import com.techsupply.erp_backend.common.domain.CalculoImportes;

/** Cálculo de importes por línea, sin efectos ni acceso a infraestructura. */
public final class CalculoCotizacion {
    private CalculoCotizacion() {}
    public record Totales(BigDecimal subtotal, BigDecimal impuestos, BigDecimal total) {}
    public static Totales calcular(List<ValoresVenta.Linea> lineas) {
        Objects.requireNonNull(lineas, "lineas");
        if (lineas.isEmpty()) throw new IllegalArgumentException("La cotización requiere líneas");
        var value = CalculoImportes.calcular(lineas.stream().map(line -> new CalculoImportes.Linea(
            line.cantidad(), line.precioUnitario(), line.descuentoPct(), line.tasaImpuesto())).toList());
        return new Totales(value.subtotal(), value.impuestos(), value.total());
    }
}
