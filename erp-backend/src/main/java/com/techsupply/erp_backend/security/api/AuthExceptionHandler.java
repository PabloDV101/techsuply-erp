package com.techsupply.erp_backend.security.api;

import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.common.api.ApiError;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = "com.techsupply.erp_backend.security.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {
    private final AuditTrail auditTrail;

    public AuthExceptionHandler(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        if (status == HttpStatus.FORBIDDEN) auditTrail.deniedAuthorization();
        return error(status, ex.getReason() == null ? status.getReasonPhrase() : ex.getReason());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "Solicitud inválida");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException ex) {
        auditTrail.deniedAuthorization();
        return error(HttpStatus.FORBIDDEN, "Permiso insuficiente");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "Los datos entran en conflicto con un registro o restricción existente");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> unavailable(DataAccessException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de datos no disponible");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        var builder = ResponseEntity.status(status);
        if (status == HttpStatus.UNAUTHORIZED) builder.header("WWW-Authenticate", "Bearer");
        return builder.body(new ApiError(status.value(), status.name(), message));
    }
}
