package com.techsupply.erp_backend.contabilidad.application.dto;

import java.util.List;

public record ConfiguracionCuentasInventarioResponse(boolean completa, List<Cuenta> cuentas) {
    public ConfiguracionCuentasInventarioResponse { cuentas = List.copyOf(cuentas); }
    public record Cuenta(String concepto, Long cuentaId, String codigo, String nombre) {}
}
