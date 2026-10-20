package com.techsupply.erp_backend.notifications.application;

import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.notifications.domain.model.NotificationStatus;
import com.techsupply.erp_backend.notifications.infrastructure.persistence.InternalNotificationRepository;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.time.Clock;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationInboxService {
    private static final Map<String, String> SORT_FIELDS = Map.of("creadaEn", "creadaEn", "id", "id");
    private final InternalNotificationRepository repository;
    private final AuthenticatedUserProvider actors;
    private final Clock clock;

    public NotificationInboxService(InternalNotificationRepository repository,
            AuthenticatedUserProvider actors, Clock clock) {
        this.repository = repository;
        this.actors = actors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(int page, int size, String sort, String direction, String leida) {
        Long userId = currentUserId();
        var pageable = PageRequests.of(page, size, sort, direction, SORT_FIELDS, "id");
        if (leida == null) return PageResponse.from(repository.findByUsuarioId(userId, pageable), NotificationResponse::from);
        if (!leida.equals("true") && !leida.equals("false")) throw invalidFilter();
        NotificationStatus status = Boolean.parseBoolean(leida) ? NotificationStatus.LEIDA : NotificationStatus.NO_LEIDA;
        return PageResponse.from(repository.findByUsuarioIdAndEstado(userId, status, pageable), NotificationResponse::from);
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador no válido");
        Long userId = currentUserId();
        repository.markRead(id, userId, clock.instant(), NotificationStatus.NO_LEIDA, NotificationStatus.LEIDA);
        return repository.findByIdAndUsuarioId(id, userId)
            .map(NotificationResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada"));
    }

    private Long currentUserId() {
        return actors.currentUser().map(actor -> actor.usuarioId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida"));
    }

    private static ResponseStatusException invalidFilter() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de lectura no válido");
    }
}
