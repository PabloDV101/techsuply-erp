package com.techsupply.erp_backend.contabilidad.application.dto;

public record CuentaContableResponse(Long id,String codigo,String nombre,String tipo,String naturaleza,short nivel,Long cuentaPadreId,boolean aceptaMovimientos,boolean activa) {}
