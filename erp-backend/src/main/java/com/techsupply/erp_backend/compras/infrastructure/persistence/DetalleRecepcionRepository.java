package com.techsupply.erp_backend.compras.infrastructure.persistence;

import com.techsupply.erp_backend.compras.domain.model.DetalleRecepcion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleRecepcionRepository extends JpaRepository<DetalleRecepcion, Long> {}
