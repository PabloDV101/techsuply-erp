-- Separate financial report access from entry and account administration.
INSERT INTO permisos(codigo, modulo, accion, descripcion)
VALUES ('CONTABILIDAD_REPORTES_LEER','CONTABILIDAD','REPORTES_LEER','Consultar mayor y balance de prueba');

INSERT INTO roles_permisos(rol_id, permiso_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permisos p
WHERE r.codigo='ADMINISTRADOR' AND p.codigo='CONTABILIDAD_REPORTES_LEER';
