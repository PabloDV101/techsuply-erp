package com.techsupply.erp_backend.common.api;

/** Error HTTP público; nunca contiene detalles internos de implementación. */
public record ApiError(int status, String code, String message) {}
