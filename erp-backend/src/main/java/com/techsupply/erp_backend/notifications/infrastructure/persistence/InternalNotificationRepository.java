package com.techsupply.erp_backend.notifications.infrastructure.persistence;

import com.techsupply.erp_backend.notifications.domain.model.InternalNotification;
import com.techsupply.erp_backend.notifications.domain.model.NotificationStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InternalNotificationRepository extends JpaRepository<InternalNotification, Long> {
    Page<InternalNotification> findByUsuarioId(Long usuarioId, Pageable page);
    Page<InternalNotification> findByUsuarioIdAndEstado(Long usuarioId, NotificationStatus status, Pageable page);
    Optional<InternalNotification> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query("update InternalNotification n set n.estado = :read, n.leidaEn = :at "
        + "where n.id = :id and n.usuarioId = :userId and n.estado = :unread")
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("at") Instant at,
        @Param("unread") NotificationStatus unread, @Param("read") NotificationStatus read);
}
