package com.techsupply.erp_backend.common.api;

import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Bounded paging and an explicit public-to-persistence sort-field allowlist. */
public final class PageRequests {
    private PageRequests() {}

    public static Pageable of(int page, int size, String sort, String direction, Map<String, String> allowedSorts) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página o tamaño no válido");
        }
        String property = allowedSorts.get(sort);
        if (property == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo de ordenación no válido");
        }
        Sort.Direction order;
        try {
            order = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dirección de ordenación no válida");
        }
        return PageRequest.of(page, size, Sort.by(order, property));
    }
}
