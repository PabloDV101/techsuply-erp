package com.techsupply.erp_backend.security.api;

import com.techsupply.erp_backend.security.application.UserManagementService;
import com.techsupply.erp_backend.security.application.dto.ActualizarEstadoUsuarioRequest;
import com.techsupply.erp_backend.security.application.dto.CrearUsuarioRequest;
import com.techsupply.erp_backend.security.application.dto.CrearRolRequest;
import com.techsupply.erp_backend.security.application.dto.CrearPermisoRequest;
import com.techsupply.erp_backend.security.application.dto.ReemplazarPermisosRolRequest;
import com.techsupply.erp_backend.security.application.dto.PermisoResponse;
import com.techsupply.erp_backend.security.application.dto.RolResponse;
import com.techsupply.erp_backend.security.application.dto.ReemplazarRolesRequest;
import com.techsupply.erp_backend.security.application.dto.UsuarioResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminUsuariosController {
    private final UserManagementService service;
    public AdminUsuariosController(UserManagementService service) { this.service = service; }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('SEGURIDAD_USUARIOS_ADMIN')")
    public List<UsuarioResponse> listarUsuarios() { return service.listarUsuarios(); }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('SEGURIDAD_USUARIOS_ADMIN')")
    public UsuarioResponse crearUsuario(@Valid @RequestBody CrearUsuarioRequest request) {
        return service.crearUsuario(request);
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('SEGURIDAD_USUARIOS_ADMIN')")
    public UsuarioResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody ActualizarEstadoUsuarioRequest request) {
        return service.cambiarEstado(id, request.activo());
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('SEGURIDAD_ROLES_ADMIN')")
    public UsuarioResponse reemplazarRoles(@PathVariable Long id, @Valid @RequestBody ReemplazarRolesRequest request) {
        return service.reemplazarRoles(id, request);
    }


    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('SEGURIDAD_ROLES_ADMIN')")
    public RolResponse crearRol(@Valid @RequestBody CrearRolRequest request) {
        return service.crearRol(request);
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('SEGURIDAD_ROLES_ADMIN')")
    public RolResponse reemplazarPermisosDeRol(@PathVariable Long id, @Valid @RequestBody ReemplazarPermisosRolRequest request) {
        return service.reemplazarPermisosDeRol(id, request);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('SEGURIDAD_PERMISOS_ADMIN')")
    public List<PermisoResponse> listarPermisos() { return service.listarPermisos(); }

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('SEGURIDAD_PERMISOS_ADMIN')")
    public PermisoResponse crearPermiso(@Valid @RequestBody CrearPermisoRequest request) {
        return service.crearPermiso(request);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('SEGURIDAD_ROLES_ADMIN')")
    public List<RolResponse> listarRoles() { return service.listarRoles(); }
}
