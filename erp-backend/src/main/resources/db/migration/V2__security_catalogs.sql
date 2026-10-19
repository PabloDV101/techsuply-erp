-- Catalogs only: no user account or password is seeded.
INSERT INTO permisos(codigo, modulo, accion, descripcion) VALUES
('SEGURIDAD_USUARIOS_ADMIN', 'SEGURIDAD', 'USUARIOS_ADMIN', 'Administrar usuarios'),
('SEGURIDAD_ROLES_ADMIN', 'SEGURIDAD', 'ROLES_ADMIN', 'Administrar y asignar roles'),
('SEGURIDAD_PERMISOS_ADMIN', 'SEGURIDAD', 'PERMISOS_ADMIN', 'Administrar permisos');
INSERT INTO roles(codigo, nombre) VALUES ('ADMINISTRADOR', 'Administrador');
INSERT INTO roles_permisos(rol_id, permiso_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permisos p WHERE r.codigo = 'ADMINISTRADOR';
