package com.techsupply.erp_backend.compras.infrastructure.persistence;

import com.techsupply.erp_backend.compras.domain.model.DetalleOrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleOrdenCompraRepository extends JpaRepository<DetalleOrdenCompra, Long> {}
