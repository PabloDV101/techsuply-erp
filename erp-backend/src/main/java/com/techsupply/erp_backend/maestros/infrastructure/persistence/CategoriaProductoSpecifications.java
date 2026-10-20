package com.techsupply.erp_backend.maestros.infrastructure.persistence;

import com.techsupply.erp_backend.maestros.application.dto.CategoriaSearchCriteria;
import com.techsupply.erp_backend.maestros.domain.model.CategoriaProducto;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Only known entity properties are referenced; the request cannot supply a SQL fragment. */
public final class CategoriaProductoSpecifications {
    private CategoriaProductoSpecifications() {}

    public static Specification<CategoriaProducto> matching(CategoriaSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.codigo() != null) {
                predicates.add(builder.equal(root.get("codigo"), criteria.codigo()));
            }
            if (criteria.nombre() != null) {
                predicates.add(builder.like(builder.lower(root.get("nombre")),
                    "%" + escapeLike(criteria.nombre()) + "%", '\\'));
            }
            if (criteria.activa() != null) {
                predicates.add(builder.equal(root.get("activa"), criteria.activa()));
            }
            if (criteria.categoriaPadreId() != null) {
                predicates.add(builder.equal(root.get("categoriaPadre").get("id"), criteria.categoriaPadreId()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
