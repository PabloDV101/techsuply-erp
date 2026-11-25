package com.techsupply.erp_backend.contabilidad.infrastructure.persistence;

import com.techsupply.erp_backend.contabilidad.domain.model.PeriodoContable;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeriodoContableRepository extends JpaRepository<PeriodoContable,Long> {
    boolean existsByFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(LocalDate fin,LocalDate inicio);
    @Query("select p from PeriodoContable p where p.estado='ABIERTO' and :fecha between p.fechaInicio and p.fechaFin")
    Optional<PeriodoContable> abiertoEn(@Param("fecha") LocalDate fecha);
}
