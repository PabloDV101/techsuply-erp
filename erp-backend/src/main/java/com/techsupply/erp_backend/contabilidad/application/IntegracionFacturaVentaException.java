package com.techsupply.erp_backend.contabilidad.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class IntegracionFacturaVentaException extends ResponseStatusException {
    private final String codigo;
    IntegracionFacturaVentaException(HttpStatus status, String codigo, String mensaje) {
        super(status, mensaje); this.codigo = codigo;
    }
    String codigo() { return codigo; }
}
