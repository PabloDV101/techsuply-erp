package com.techsupply.erp_backend.reporteria.application;

/** Indicadores iniciales y autoridad necesaria para consultar su fuente. */
public enum IndicadorDashboard {
    COMPRAS_FACTURADO_SIN_IMPUESTOS("COMPRAS_FACTURAS_PROVEEDOR_LEER"),
    VENTAS_FACTURADO_SIN_IMPUESTOS("VENTAS_FACTURAS_LEER"),
    INVENTARIO_PRODUCTOS_ALERTA("INVENTARIO_ALERTAS_LEER"),
    FINANZAS_RESULTADO("CONTABILIDAD_REPORTES_LEER");

    private final String permiso;

    IndicadorDashboard(String permiso) { this.permiso = permiso; }

    public String permiso() { return permiso; }
}
