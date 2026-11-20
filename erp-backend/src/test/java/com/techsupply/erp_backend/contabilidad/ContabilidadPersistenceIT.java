package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.contabilidad.domain.model.*;
import com.techsupply.erp_backend.contabilidad.infrastructure.persistence.*;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class ContabilidadPersistenceIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired EntityManager em;
    @Autowired CuentaContableRepository cuentas;
    @Autowired PeriodoContableRepository periodos;
    @Autowired AsientoContableRepository asientos;
    @Autowired PartidaAsientoRepository partidas;

    @Test @Transactional void persisteYRecargaBorradorSinContabilizar() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        Long usuario = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?, 'hash') returning id", Long.class, "cont" + key, key + "@example.test");
        var padre = cuentas.save(new CuentaContable("P" + key, "Grupo", TipoCuenta.ACTIVO, NaturalezaCuenta.DEUDORA, (short) 1, null, false));
        var cuenta = cuentas.save(new CuentaContable("C" + key, "Cuenta", TipoCuenta.ACTIVO, NaturalezaCuenta.DEUDORA, (short) 2, padre.getId(), true));
        var periodo = periodos.save(new PeriodoContable(LocalDate.of(2042, 1, 1), LocalDate.of(2042, 1, 31)));
        var asiento = asientos.save(new AsientoContable("F" + key, periodo.getId(), LocalDate.of(2042, 1, 31), "Prueba", "K" + key, usuario));
        var linea2 = partidas.save(new PartidaAsiento(asiento.getId(), (short) 2, cuenta.getId(), BigDecimal.ZERO, new BigDecimal("999999999999.99"), null));
        var linea1 = partidas.save(new PartidaAsiento(asiento.getId(), (short) 1, cuenta.getId(), new BigDecimal("999999999999.99"), BigDecimal.ZERO, "Prueba"));
        em.flush();
        em.clear();
        assertThat(cuentas.findByCodigo("C" + key).orElseThrow().getCuentaPadreId()).isEqualTo(padre.getId());
        assertThat(cuentas.findById(cuenta.getId()).orElseThrow().getNaturaleza()).isEqualTo(NaturalezaCuenta.DEUDORA);
        assertThat(periodos.findById(periodo.getId()).orElseThrow().contiene(LocalDate.of(2042, 1, 31))).isTrue();
        var recargado = asientos.findByClaveIdempotencia("K" + key).orElseThrow();
        assertThat(recargado.getEstado()).isEqualTo(EstadoAsientoContable.BORRADOR);
        assertThat(recargado.getContabilizadoEn()).isNull();
        assertThat(recargado.getOrigenModulo()).isEqualTo("MANUAL");
        var lineas = partidas.findByAsientoIdOrderByNumeroLineaAsc(asiento.getId());
        assertThat(lineas).extracting(PartidaAsiento::getId).containsExactly(linea1.getId(), linea2.getId());
        assertThat(lineas.getFirst().getDebe()).isEqualByComparingTo("999999999999.99");
        assertThat(BalanceAsiento.calcular(lineas.stream().map(PartidaAsiento::importe).toList()).equilibrado()).isTrue();
        assertThat(db.queryForObject("select count(*) from asientos_contables where id=? and estado='CONTABILIZADO'", Integer.class, asiento.getId())).isZero();
    }
}
