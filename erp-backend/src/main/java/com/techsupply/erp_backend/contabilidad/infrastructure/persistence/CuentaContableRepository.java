package com.techsupply.erp_backend.contabilidad.infrastructure.persistence;

import com.techsupply.erp_backend.contabilidad.domain.model.CuentaContable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CuentaContableRepository extends JpaRepository<CuentaContable,Long> {
    boolean existsByCodigoIgnoreCase(String codigo);
    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo,Long id);
    @Query("select c from CuentaContable c where c.activa=true and c.aceptaMovimientos=true")
    java.util.List<CuentaContable> cuentasActivas();
}
