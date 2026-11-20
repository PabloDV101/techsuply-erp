package com.techsupply.erp_backend.contabilidad.application.dto;

import java.util.List;

public record ConfiguracionFacturaVentaResponse(boolean configurada, List<CuentaIntegracion> cuentas) {
    public record CuentaIntegracion(String concepto, Long cuentaId, String codigo, String nombre) {}
}
