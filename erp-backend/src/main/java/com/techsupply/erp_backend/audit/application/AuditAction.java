package com.techsupply.erp_backend.audit.application;

/** Server-owned event catalog. No client value is ever used as an action name. */
public enum AuditAction {
    LOGIN_SUCCEEDED("SEGURIDAD", "SESION"),
    LOGIN_DENIED("SEGURIDAD", "ACCESO"),
    LOGOUT("SEGURIDAD", "SESION"),
    AUTHORIZATION_DENIED("SEGURIDAD", "ACCESO"),
    ADMIN_BOOTSTRAPPED("SEGURIDAD", "USUARIO"),
    USER_CREATED("SEGURIDAD", "USUARIO"),
    USER_STATUS_CHANGED("SEGURIDAD", "USUARIO"),
    USER_ROLES_REPLACED("SEGURIDAD", "USUARIO"),
    ROLE_CREATED("SEGURIDAD", "ROL"),
    ROLE_PERMISSIONS_REPLACED("SEGURIDAD", "ROL"),
    PERMISSION_CREATED("SEGURIDAD", "PERMISO"),
    CATEGORY_CREATED("MAESTROS", "CATEGORIA_PRODUCTO"),
    CATEGORY_UPDATED("MAESTROS", "CATEGORIA_PRODUCTO"),
    CATEGORY_STATUS_CHANGED("MAESTROS", "CATEGORIA_PRODUCTO");

    private final String module;
    private final String resource;

    AuditAction(String module, String resource) {
        this.module = module;
        this.resource = resource;
    }

    public String module() { return module; }
    public String resource() { return resource; }
}
