package com.techsupply.erp_backend.common.api;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Default HTTP error contract for ERP APIs. Module-specific advices may take
 * precedence and apply domain-specific messages while preserving ApiError's shape.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<ApiError> invalidBody(BindException ex) {
        List<String> violations = ex.getFieldErrors().stream()
            .map(GlobalApiExceptionHandler::formatFieldError)
            .distinct()
            .sorted()
            .toList();
        return badRequest(violations.isEmpty() ? "Solicitud inválida" : "Solicitud inválida: " + String.join("; ", violations));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> invalidMethod(HandlerMethodValidationException ex) {
        return badRequest("Uno o más parámetros de la solicitud no son válidos");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraintViolation(ConstraintViolationException ex) {
        List<String> violations = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .distinct()
            .sorted()
            .toList();
        return badRequest(violations.isEmpty() ? "Solicitud inválida" : "Solicitud inválida: " + String.join("; ", violations));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> malformedRequest(Exception ex) {
        return badRequest("El cuerpo o los parámetros de la solicitud no son válidos");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException ex) {
        HttpStatus knownStatus = HttpStatus.resolve(ex.getStatusCode().value());
        String message = switch (knownStatus == null ? HttpStatus.INTERNAL_SERVER_ERROR : knownStatus) {
            case BAD_REQUEST -> "Solicitud inválida";
            case UNAUTHORIZED -> "Autenticación requerida o credenciales inválidas";
            case FORBIDDEN -> "Permiso insuficiente";
            case NOT_FOUND -> "Recurso no encontrado";
            case CONFLICT -> "Conflicto con el estado actual del recurso";
            case SERVICE_UNAVAILABLE -> "Servicio temporalmente no disponible";
            default -> ex.getStatusCode().is4xxClientError() ? "La solicitud no pudo procesarse" : "Error interno del servidor";
        };
        return error(ex.getStatusCode(), message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "Los datos entran en conflicto con un registro o restricción existente");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> unavailable(DataAccessException ex) {
        log.error("Database operation failed while processing an API request", ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de datos no disponible");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido para este recurso");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido");
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiError> notFound(Exception ex) {
        return error(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Unhandled exception while processing an API request", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    private static String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<ApiError> badRequest(String message) {
        return error(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<ApiError> error(HttpStatusCode status, String message) {
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        return ResponseEntity.status(status)
            .body(new ApiError(status.value(), knownStatus == null ? "HTTP_" + status.value() : knownStatus.name(), message));
    }
}
