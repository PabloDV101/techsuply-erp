package com.techsupply.erp_backend.reporteria.application;

import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Valida criterios y selecciona las fuentes que el actor puede consultar. */
@Service
public class PrepararDashboardService {
    private final AuthenticatedUserProvider users;

    public PrepararDashboardService(AuthenticatedUserProvider users) { this.users = users; }

    @PreAuthorize("isAuthenticated()")
    public CriteriosDashboard preparar(LocalDate from, LocalDate to, Long periodId) {
        var actor = users.currentUser().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida"));
        var permitidos = Arrays.stream(IndicadorDashboard.values())
            .filter(indicador -> actor.authorities().contains(indicador.permiso()))
            .collect(Collectors.toUnmodifiableSet());
        if (permitidos.isEmpty()) throw new AccessDeniedException("Permiso insuficiente");
        if (from == null || to == null || from.isAfter(to) || (periodId != null && periodId < 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Criterios de reporte inválidos");
        }
        return new CriteriosDashboard(from, to, periodId, permitidos);
    }
}
