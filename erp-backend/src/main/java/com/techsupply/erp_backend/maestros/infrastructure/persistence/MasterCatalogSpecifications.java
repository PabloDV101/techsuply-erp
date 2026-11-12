package com.techsupply.erp_backend.maestros.infrastructure.persistence;

import com.techsupply.erp_backend.maestros.application.dto.MasterCatalogSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Shared only for the codigo/activo columns present in all three V6 catalogs. */
public final class MasterCatalogSpecifications {
    private MasterCatalogSpecifications() {}

    public static <T> Specification<T> matching(MasterCatalogSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.codigo() != null) predicates.add(builder.equal(root.get("codigo"), criteria.codigo()));
            if (criteria.activo() != null) predicates.add(builder.equal(root.get("activo"), criteria.activo()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
