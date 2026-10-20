package com.techsupply.erp_backend.security.application;

import com.techsupply.erp_backend.security.application.dto.ActualizarEstadoUsuarioRequest;
import com.techsupply.erp_backend.security.application.dto.CrearUsuarioRequest;
import com.techsupply.erp_backend.security.application.dto.CrearRolRequest;
import com.techsupply.erp_backend.security.application.dto.CrearPermisoRequest;
import com.techsupply.erp_backend.security.application.dto.ReemplazarPermisosRolRequest;
import com.techsupply.erp_backend.security.application.dto.PermisoResponse;
import com.techsupply.erp_backend.security.domain.model.Permiso;
import com.techsupply.erp_backend.security.application.dto.RolResponse;
import com.techsupply.erp_backend.security.application.dto.ReemplazarRolesRequest;
import com.techsupply.erp_backend.security.application.dto.UsuarioResponse;
import com.techsupply.erp_backend.security.domain.model.Rol;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import com.techsupply.erp_backend.security.infrastructure.persistence.PermisoRepository;
import com.techsupply.erp_backend.security.infrastructure.persistence.RolRepository;
import com.techsupply.erp_backend.security.infrastructure.persistence.TokenSesionRepository;
import com.techsupply.erp_backend.security.infrastructure.persistence.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserManagementService {
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final TokenSesionRepository tokenSesionRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
                                 PermisoRepository permisoRepository, TokenSesionRepository tokenSesionRepository, PasswordEncoder passwordEncoder,
                                 AuthenticatedUserProvider authenticatedUserProvider) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.tokenSesionRepository = tokenSesionRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarUsuarios() {
        return usuarioRepository.listarConRolesYPermisos().stream().map(AuthService::usuarioResponse).toList();
    }

    @Transactional
    public UsuarioResponse crearUsuario(CrearUsuarioRequest request) {
        String username = normalizar(request.username());
        String correo = normalizar(request.correo());
        usuarioRepository.bloquearAdministracion();
        if (usuarioRepository.existsByUsernameIgnoreCaseOrCorreoIgnoreCase(username, username)
                || usuarioRepository.existsByUsernameIgnoreCaseOrCorreoIgnoreCase(correo, correo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario o correo ya está registrado");
        }
        if (request.empleadoId() != null && request.clienteId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una cuenta no puede vincularse simultáneamente a empleado y cliente");
        }
        Set<Rol> roles = buscarRoles(request.roles());
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setCorreo(correo);
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setEmpleadoId(request.empleadoId());
        usuario.setClienteId(request.clienteId());
        usuario.setActivo(true);
        usuario.setRoles(roles);
        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        registrarAsignadorDeRoles(guardado.getId());
        return AuthService.usuarioResponse(guardado);
    }

    @Transactional
    public UsuarioResponse cambiarEstado(Long id, boolean activo) {
        usuarioRepository.bloquearAdministracion();
        Usuario usuario = usuarioRepository.buscarParaActualizacion(id).orElseThrow(this::usuarioNoEncontrado);
        boolean esAdministrador = usuario.getRoles().stream().anyMatch(r -> "ADMINISTRADOR".equals(r.getCodigo()) && r.isActivo());
        if (!activo && usuario.isActivo() && esAdministrador && usuarioRepository.contarAdministradoresActivos() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede desactivar al último administrador activo");
        }
        usuario.setActivo(activo);
        if (!activo) tokenSesionRepository.revocarSesionesActivasDeUsuario(id, Instant.now());
        return AuthService.usuarioResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse reemplazarRoles(Long id, ReemplazarRolesRequest request) {
        usuarioRepository.bloquearAdministracion();
        Usuario usuario = usuarioRepository.buscarParaActualizacion(id).orElseThrow(this::usuarioNoEncontrado);
        Set<Rol> nuevosRoles = buscarRoles(request.roles());
        boolean eraAdministrador = usuario.getRoles().stream().anyMatch(r -> "ADMINISTRADOR".equals(r.getCodigo()) && r.isActivo());
        boolean seguiraAdministrador = nuevosRoles.stream().anyMatch(r -> "ADMINISTRADOR".equals(r.getCodigo()) && r.isActivo());
        if (usuario.isActivo() && eraAdministrador && !seguiraAdministrador && usuarioRepository.contarAdministradoresActivos() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede retirar el rol al último administrador activo");
        }
        usuario.setRoles(nuevosRoles);
        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        registrarAsignadorDeRoles(guardado.getId());
        tokenSesionRepository.revocarSesionesActivasDeUsuario(id, Instant.now());
        return AuthService.usuarioResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<RolResponse> listarRoles() {
        return rolRepository.findAllByActivoTrueOrderByCodigoAsc().stream()
            .map(rol -> {
                Rol conPermisos = rolRepository.buscarConPermisosPorId(rol.getId()).orElse(rol);
                return rolResponse(conPermisos);
            }).toList();
    }


    @Transactional
    public RolResponse crearRol(CrearRolRequest request) {
        String codigo = request.codigo().trim().toUpperCase(Locale.ROOT);
        if (rolRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El código del rol ya existe");
        }
        Rol rol = new Rol();
        rol.setCodigo(codigo);
        rol.setNombre(request.nombre().trim());
        rol.setDescripcion(request.descripcion() == null ? null : request.descripcion().trim());
        rol.setActivo(true);
        rol.setPermisos(buscarPermisos(request.permisos()));
        return rolResponse(rolRepository.save(rol));
    }

    @Transactional
    public RolResponse reemplazarPermisosDeRol(Long id, ReemplazarPermisosRolRequest request) {
        Rol rol = rolRepository.buscarConPermisosPorId(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado"));
        Set<Permiso> nuevosPermisos = buscarPermisos(request.permisos());
        if ("ADMINISTRADOR".equals(rol.getCodigo())) {
            Set<String> codigos = nuevosPermisos.stream().map(Permiso::getCodigo).collect(Collectors.toSet());
            if (!codigos.contains("SEGURIDAD_USUARIOS_ADMIN") || !codigos.contains("SEGURIDAD_ROLES_ADMIN")
                    || !codigos.contains("SEGURIDAD_PERMISOS_ADMIN")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pueden retirar al rol ADMINISTRADOR sus permisos administrativos esenciales");
            }
        }
        rol.setPermisos(nuevosPermisos);
        Rol guardado = rolRepository.save(rol);
        return rolResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<PermisoResponse> listarPermisos() {
        return permisoRepository.findAllByOrderByModuloAscAccionAsc().stream()
            .map(p -> new PermisoResponse(p.getId(), p.getCodigo(), p.getModulo(), p.getAccion(), p.getDescripcion()))
            .toList();
    }

    @Transactional
    public PermisoResponse crearPermiso(CrearPermisoRequest request) {
        String codigo = request.codigo().trim().toUpperCase(Locale.ROOT);
        String modulo = request.modulo().trim().toUpperCase(Locale.ROOT);
        String accion = request.accion().trim().toUpperCase(Locale.ROOT);
        if (permisoRepository.existsByCodigoIgnoreCase(codigo)
                || permisoRepository.existsByModuloIgnoreCaseAndAccionIgnoreCase(modulo, accion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El código o la combinación módulo/acción ya existen");
        }
        Permiso permiso = new Permiso();
        permiso.setCodigo(codigo);
        permiso.setModulo(modulo);
        permiso.setAccion(accion);
        permiso.setDescripcion(request.descripcion() == null ? null : request.descripcion().trim());
        Permiso guardado = permisoRepository.save(permiso);
        return new PermisoResponse(guardado.getId(), guardado.getCodigo(), guardado.getModulo(), guardado.getAccion(), guardado.getDescripcion());
    }

    private Set<Permiso> buscarPermisos(Set<String> codigosEntrada) {
        usuarioRepository.bloquearAdministracion();
        AuthenticatedActor actor = authenticatedUserProvider.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida"));
        Set<String> codigos = codigosEntrada.stream().map(c -> c.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        List<Permiso> encontrados = permisoRepository.findByCodigoIn(codigos);
        if (encontrados.size() != codigos.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno o más permisos no existen");
        }
        if (!actor.authorities().containsAll(codigos)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede delegar permisos que no posee");
        }
        return new java.util.HashSet<>(encontrados);
    }

    private RolResponse rolResponse(Rol rol) {
        return new RolResponse(rol.getId(), rol.getCodigo(), rol.getNombre(), rol.getDescripcion(),
            rol.getPermisos().stream().map(Permiso::getCodigo).collect(Collectors.toCollection(TreeSet::new)));
    }


    private void registrarAsignadorDeRoles(Long usuarioId) {
        AuthenticatedActor actor = authenticatedUserProvider.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida"));
        usuarioRepository.registrarAsignadorDeRoles(usuarioId, actor.usuarioId(), Instant.now());
    }

    private Usuario buscarUsuario(Long id) {
        return usuarioRepository.findById(id).orElseThrow(this::usuarioNoEncontrado);
    }

    private ResponseStatusException usuarioNoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
    }

    private Set<Rol> buscarRoles(Set<String> codigosEntrada) {
        Set<String> codigos = codigosEntrada.stream().map(c -> c.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        List<Rol> encontrados = rolRepository.findByCodigoInAndActivoTrue(codigos);
        if (encontrados.size() != codigos.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno o más roles no existen o están inactivos");
        }
        AuthenticatedActor actor = authenticatedUserProvider.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida"));
        if (!actor.authorities().contains("SEGURIDAD_ROLES_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere permiso para asignar roles");
        }
        Set<String> delegados = encontrados.stream().flatMap(r -> r.getPermisos().stream())
            .map(Permiso::getCodigo).collect(Collectors.toSet());
        if (!actor.authorities().containsAll(delegados)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede delegar permisos que no posee");
        }
        return new java.util.HashSet<>(encontrados);
    }

    private String normalizar(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
