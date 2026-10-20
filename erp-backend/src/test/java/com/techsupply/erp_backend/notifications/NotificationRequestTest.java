package com.techsupply.erp_backend.notifications;

import com.techsupply.erp_backend.notifications.application.NotificationRequest;
import com.techsupply.erp_backend.notifications.application.NotificationType;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationRequestTest {
    @Test void snapshotsRecipientsAndRejectsInvalidValues() {
        Set<Long> recipients = new HashSet<>(Set.of(1L));
        var request = new NotificationRequest(NotificationType.RESULTADO_OPERACION, "MAESTROS",
            "CATEGORIA_PRODUCTO", 3L, "create:3", recipients);
        recipients.add(2L);
        assertThat(request.recipientIds()).containsExactly(1L);
        assertThatThrownBy(() -> new NotificationRequest(NotificationType.RESULTADO_OPERACION,
            "MAESTROS", "CATEGORIA_PRODUCTO", 3L, "create:3", Set.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationRequest(NotificationType.RESULTADO_OPERACION,
            "MAESTROS", "CATEGORIA_PRODUCTO", 3L, "create:3", Set.of(0L)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationRequest(NotificationType.RESULTADO_OPERACION,
            "MAESTROS;DROP", "CATEGORIA_PRODUCTO", 3L, "create:3", Set.of(1L)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
