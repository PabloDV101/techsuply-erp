package com.techsupply.erp_backend.compras.infrastructure.persistence;

import com.techsupply.erp_backend.compras.domain.model.OrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long>, JpaSpecificationExecutor<OrdenCompra> {
    @Query(value = "select * from ordenes_compra where id = :id for update", nativeQuery = true)
    Optional<OrdenCompra> bloquearPorId(@Param("id") Long id);

    @Query("select distinct o from OrdenCompra o left join fetch o.detalles where o.id in :ids")
    List<OrdenCompra> conDetalles(@Param("ids") List<Long> ids);
}
