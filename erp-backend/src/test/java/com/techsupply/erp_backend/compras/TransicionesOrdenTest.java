package com.techsupply.erp_backend.compras;

import com.techsupply.erp_backend.compras.domain.model.*;
import com.techsupply.erp_backend.maestros.domain.model.*;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransicionesOrdenTest {
    private final Instant now=Instant.parse("2026-11-24T12:00:00Z");
    private final Usuario creator=mock(Usuario.class), approver=mock(Usuario.class);
    private final Proveedor supplier=mock(Proveedor.class);
    private final Departamento department=mock(Departamento.class);
    private final Producto product=mock(Producto.class);
    private OrdenCompra order() {
        when(creator.getId()).thenReturn(1L); when(approver.getId()).thenReturn(2L); when(product.getId()).thenReturn(3L);
        return new OrdenCompra("OC1",supplier,department,creator,now,null);
    }
    private DetalleOrdenCompra detail() {
        return new DetalleOrdenCompra(product,new BigDecimal("20"),new BigDecimal("10"),BigDecimal.ZERO,new BigDecimal("16"));
    }
    @Test void exigeAprobadorDistintoYConservaTotalesHastaRecepcion() {
        OrdenCompra purchase=order();
        assertThatThrownBy(purchase::enviarAprobacion).isInstanceOf(IllegalStateException.class);
        purchase.agregarDetalle(detail());
        assertThat(purchase.getSubtotal()).isEqualByComparingTo("200");
        assertThat(purchase.getImpuestos()).isEqualByComparingTo("32");
        assertThat(purchase.getTotal()).isEqualByComparingTo("232");
        assertThatThrownBy(()->purchase.agregarDetalle(detail())).isInstanceOf(IllegalArgumentException.class);
        assertThat(purchase.getDetalles()).hasSize(1);
        purchase.enviarAprobacion();
        assertThatThrownBy(()->purchase.aprobar(creator,now)).isInstanceOf(IllegalArgumentException.class);
        assertThat(purchase.getEstado()).isEqualTo(EstadoOrdenCompra.PENDIENTE_APROBACION);
        purchase.aprobar(approver,now); purchase.registrarEnvio();
        purchase.registrarRecepcion(false,false); assertThat(purchase.getEstado()).isEqualTo(EstadoOrdenCompra.ENVIADA);
        purchase.registrarRecepcion(false,true); assertThat(purchase.getEstado()).isEqualTo(EstadoOrdenCompra.PARCIALMENTE_RECIBIDA);
        purchase.registrarRecepcion(true,true); assertThat(purchase.getEstado()).isEqualTo(EstadoOrdenCompra.RECIBIDA);
        assertThatThrownBy(()->purchase.registrarRecepcion(true,true)).isInstanceOf(IllegalStateException.class);
    }
}
