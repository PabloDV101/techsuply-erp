package com.techsupply.erp_backend.ventas;

import com.techsupply.erp_backend.ventas.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TransicionesVentaTest {
    private final LocalDate today=LocalDate.of(2026,11,24);
    private final Instant now=Instant.parse("2026-11-24T12:00:00Z");
    private Cotizacion quote() {
        return new Cotizacion("C1",1L,2L,now,today,"key",new CalculoCotizacion.Totales(new BigDecimal("27.00"),new BigDecimal("4.32"),new BigDecimal("31.32")));
    }
    @Test void cotizacionVigenteEnFechaLimiteYTransicionesSonIrreversibles() {
        Cotizacion expired=quote();
        assertThatThrownBy(()->expired.emitir(today.plusDays(1))).isInstanceOf(IllegalStateException.class);
        assertThat(expired.getEstado()).isEqualTo(EstadoCotizacion.BORRADOR);
        Cotizacion c=quote();
        assertThatThrownBy(()->c.aceptar(today)).isInstanceOf(IllegalStateException.class);
        c.emitir(today);
        assertThatThrownBy(()->c.aceptar(today.plusDays(1))).isInstanceOf(IllegalStateException.class);
        c.aceptar(today);
        assertThat(c.getEstado()).isEqualTo(EstadoCotizacion.ACEPTADA);
        assertThatThrownBy(c::cancelar).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->c.emitir(today)).isInstanceOf(IllegalStateException.class);
        Cotizacion cancelled=quote(); cancelled.cancelar();
        assertThatThrownBy(()->cancelled.emitir(today)).isInstanceOf(IllegalStateException.class);
    }
    @Test void pedidoControlaDespachoParcialFinalYCancelacionConMotivo() {
        PedidoVenta order=new PedidoVenta("P1",quote(),3L,4L,now,"key");
        assertThat(order.getTotal()).isEqualByComparingTo("31.32");
        assertThatThrownBy(()->order.registrarDespacho(false)).isInstanceOf(IllegalStateException.class);
        order.confirmar();
        assertThatThrownBy(order::confirmar).isInstanceOf(IllegalStateException.class);
        order.registrarDespacho(false);
        assertThat(order.getEstado()).isEqualTo(EstadoPedidoVenta.PARCIALMENTE_DESPACHADO);
        assertThatThrownBy(()->order.cancelar("  ",4L,now)).isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getEstado()).isEqualTo(EstadoPedidoVenta.PARCIALMENTE_DESPACHADO);
        order.registrarDespacho(true);
        assertThat(order.getEstado()).isEqualTo(EstadoPedidoVenta.DESPACHADO);
        assertThatThrownBy(()->order.cancelar("motivo",4L,now)).isInstanceOf(IllegalStateException.class);
        PedidoVenta canceled=new PedidoVenta("P2",quote(),3L,4L,now,"other");
        canceled.cancelar("Cliente",4L,now);
        assertThat(canceled.getCanceladoPorUsuarioId()).isEqualTo(4L);
        assertThat(canceled.getMotivoCancelacion()).isEqualTo("Cliente");
        assertThatThrownBy(canceled::confirmar).isInstanceOf(IllegalStateException.class);
    }
    @Test void despachoConfirmadoNoPuedeCancelarseNiConfirmarseDosVeces() {
        Despacho dispatch=new Despacho("D1",1L,2L,3L,now,"k");
        dispatch.confirmar();
        assertThat(dispatch.getEstado()).isEqualTo(EstadoDespacho.CONFIRMADO);
        assertThatThrownBy(dispatch::confirmar).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(dispatch::cancelar).isInstanceOf(IllegalStateException.class);
        Despacho canceled=new Despacho("D2",1L,2L,3L,now,"k2"); canceled.cancelar();
        assertThatThrownBy(canceled::confirmar).isInstanceOf(IllegalStateException.class);
    }
}
