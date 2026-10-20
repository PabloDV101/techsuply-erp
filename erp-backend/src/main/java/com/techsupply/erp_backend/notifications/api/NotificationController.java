package com.techsupply.erp_backend.notifications.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.notifications.application.NotificationInboxService;
import com.techsupply.erp_backend.notifications.application.NotificationResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationInboxService service;
    public NotificationController(NotificationInboxService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "creadaEn") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String leida) {
        return service.list(page, size, sort, direction, leida);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public NotificationResponse markRead(@PathVariable Long id) {
        return service.markRead(id);
    }
}
