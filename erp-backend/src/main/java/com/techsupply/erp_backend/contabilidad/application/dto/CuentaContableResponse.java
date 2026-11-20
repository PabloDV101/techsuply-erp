package com.techsupply.erp_backend.contabilidad.application.dto;

import com.techsupply.erp_backend.contabilidad.domain.model.CuentaContable;
import com.techsupply.erp_backend.contabilidad.domain.model.NaturalezaCuenta;
import com.techsupply.erp_backend.contabilidad.domain.model.TipoCuenta;

public record CuentaContableResponse(Long id, String codigo, String nombre, TipoCuenta tipo,
    NaturalezaCuenta naturaleza, short nivel, Long cuentaPadreId, boolean aceptaMovimientos, boolean activa) {
    public static CuentaContableResponse from(CuentaContable cuenta) {
        return new CuentaContableResponse(cuenta.getId(), cuenta.getCodigo(), cuenta.getNombre(),
            cuenta.getTipo(), cuenta.getNaturaleza(), cuenta.getNivel(), cuenta.getCuentaPadreId(),
            cuenta.isAceptaMovimientos(), cuenta.isActiva());
    }
}
