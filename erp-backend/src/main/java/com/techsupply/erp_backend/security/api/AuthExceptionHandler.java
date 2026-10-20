package com.techsupply.erp_backend.security.api;

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
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<AuthError> status(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return error(status, ex.getReason() == null ? status.getReasonPhrase() : ex.getReason());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class})
    ResponseEntity<AuthError> invalid(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "Solicitud inválida");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<AuthError> denied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Permiso insuficiente");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<AuthError> conflict(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "Los datos entran en conflicto con un registro o restricción existente");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<AuthError> unavailable(DataAccessException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de datos no disponible");
    }

    private ResponseEntity<AuthError> error(HttpStatus status, String message) {
        var builder = ResponseEntity.status(status);
        if (status == HttpStatus.UNAUTHORIZED) builder.header("WWW-Authenticate", "Bearer");
        return builder.body(new AuthError(status.value(), status.name(), message));
    }
}
