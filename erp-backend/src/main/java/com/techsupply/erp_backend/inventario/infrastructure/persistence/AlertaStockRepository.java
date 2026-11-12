package com.techsupply.erp_backend.inventario.infrastructure.persistence;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.dto.AlertaStockResponse;
import java.util.ArrayList;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlertaStockRepository {
    private final NamedParameterJdbcTemplate db;

    public AlertaStockRepository(NamedParameterJdbcTemplate db) { this.db = db; }

    public PageResponse<AlertaStockResponse> buscar(Pageable page, Long productoId) {
        // One statement provides a single PostgreSQL snapshot for both the page and its total,
        // including when the requested page is empty. The optional predicate is selected from constants.
        String productFilter = productoId == null ? "" : " and p.id = :productoId";
        String sql = """
            with alertas as materialized (
                select p.id as producto_id, p.sku, p.nombre, p.punto_reorden, p.cantidad_reorden,
                    coalesce(sum(e.cantidad_fisica - e.cantidad_reservada)
                        filter (where a.id is not null), 0) as disponible_global
                from productos p
                left join existencias e on e.producto_id = p.id
                left join almacenes a on a.id = e.almacen_id and a.activo
                where p.activo and p.punto_reorden > 0
            """ + productFilter + """
                group by p.id
                having coalesce(sum(e.cantidad_fisica - e.cantidad_reservada)
                    filter (where a.id is not null), 0) <= p.punto_reorden
            ), total as (select count(*) as cantidad from alertas)
            select a.producto_id, a.sku, a.nombre, a.disponible_global, a.punto_reorden, a.cantidad_reorden, t.cantidad
            from total t left join lateral (
                select * from alertas order by producto_id asc limit :limite offset :desplazamiento
            ) a on true order by a.producto_id asc
            """;
        var params = new MapSqlParameterSource().addValue("limite", page.getPageSize())
            .addValue("desplazamiento", page.getOffset()).addValue("productoId", productoId);
        return db.query(sql, params, rs -> {
            var items = new ArrayList<AlertaStockResponse>();
            long total = 0;
            while (rs.next()) {
                total = rs.getLong("cantidad");
                Long id = rs.getObject("producto_id", Long.class);
                if (id != null) items.add(new AlertaStockResponse(id, rs.getString("sku"), rs.getString("nombre"),
                    rs.getBigDecimal("disponible_global"), rs.getBigDecimal("punto_reorden"), rs.getBigDecimal("cantidad_reorden")));
            }
            long pages = total / page.getPageSize() + (total % page.getPageSize() == 0 ? 0 : 1);
            return new PageResponse<>(items, page.getPageNumber(), page.getPageSize(), total, (int) Math.min(pages, Integer.MAX_VALUE));
        });
    }
}
