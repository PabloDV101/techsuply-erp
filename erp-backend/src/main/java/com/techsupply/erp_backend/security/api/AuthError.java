package com.techsupply.erp_backend.security.api;

public record AuthError(int status, String code, String message) {}
