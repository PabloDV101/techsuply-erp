package com.techsupply.erp_backend.ventas;

import com.techsupply.erp_backend.maestros.domain.model.Cliente;
import com.techsupply.erp_backend.inventario.infrastructure.persistence.MovimientoInventarioRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.ClienteRepository;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import com.techsupply.erp_backend.ventas.domain.model.*;
import com.techsupply.erp_backend.ventas.infrastructure.persistence.*;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class VentasPersistenceIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired EntityManager em;
    @Autowired ClienteRepository clientes;
    @Autowired CotizacionRepository cotizaciones;
    @Autowired DetalleCotizacionRepository detallesCotizacion;
    @Autowired PedidoVentaRepository pedidos;
    @Autowired DetallePedidoRepository detallesPedido;
    @Autowired DespachoRepository despachos;
    @Autowired DetalleDespachoRepository detallesDespacho;
    @Autowired MovimientoInventarioRepository movements;
    private long id(String sql, Object... values) { return db.queryForObject(sql, Long.class, values); }

    @Test @Transactional void guardaClienteYRecargaDocumentosSinAlterarExistencias() {
        String key = "T" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        String userName = key.toLowerCase(Locale.ROOT);
        long user = id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", userName, userName + "@example.test", "hash");
        long category = id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",key,"Categoria");
        long product = id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key,"Producto",category);
        long warehouse = id("insert into almacenes(codigo,nombre) values (?,?) returning id",key,"Almacen");
        Cliente client = clientes.save(new Cliente(key.toLowerCase(Locale.ROOT), " Cliente de prueba "));
        em.flush(); long clientId = client.getId();
        // Synthetic snapshots exercise mappings; no creation or confirmation workflow is claimed.
        long quote = id("insert into cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,subtotal,impuestos,total,clave_idempotencia) values (?,?,?,date '2026-11-30','ACEPTADA',27,4.32,31.32,?) returning id",key,clientId,user,key);
        long quoteLine = id("insert into detalles_cotizacion(cotizacion_id,producto_id,cantidad,precio_unitario,descuento_pct,tasa_impuesto) values (?,?,3,10,10,16) returning id",quote,product);
        long order = id("insert into pedidos_venta(folio,cliente_id,vendedor_usuario_id,cotizacion_id,almacen_id,creado_por_usuario_id,canal,modalidad,estado,subtotal,impuestos,total,clave_idempotencia) values (?,?,?,?,?,?,'INTERNO','CONTADO','PARCIALMENTE_DESPACHADO',27,4.32,31.32,?) returning id",key,clientId,user,quote,warehouse,user,key);
        long orderLine = id("insert into detalles_pedido(pedido_id,producto_id,cantidad,cantidad_reservada,cantidad_despachada,precio_unitario,descuento_pct,tasa_impuesto) values (?,?,3,2,1,10,10,16) returning id",order,product);
        long dispatch = id("insert into despachos(folio,pedido_id,almacen_id,registrado_por_usuario_id,estado,clave_idempotencia) values (?,?,?,?,'BORRADOR',?) returning id",key,order,warehouse,user,key);
        long dispatchLine = id("insert into detalles_despacho(despacho_id,detalle_pedido_id,cantidad) values (?,?,1) returning id",dispatch,orderLine);
        em.clear();
        assertThat(clientes.findByCodigo(key).orElseThrow().getId()).isEqualTo(clientId);
        assertThat(clientes.bloquearPorId(clientId)).isPresent();
        var loadedQuote = cotizaciones.findByClaveIdempotencia(key).orElseThrow();
        assertThat(loadedQuote.getEstado()).isEqualTo(EstadoCotizacion.ACEPTADA);
        assertThat(loadedQuote.getTotal()).isEqualByComparingTo("31.32");
        assertThat(cotizaciones.bloquearPorId(quote)).isPresent();
        assertThat(detallesCotizacion.findByCotizacionIdOrderByIdAsc(quote)).extracting(DetalleCotizacion::getId).containsExactly(quoteLine);
        var loadedOrder = pedidos.findByCotizacionId(quote).orElseThrow();
        assertThat(loadedOrder.getModalidad()).isEqualTo(ModalidadVenta.CONTADO);
        assertThat(loadedOrder.getEstado()).isEqualTo(EstadoPedidoVenta.PARCIALMENTE_DESPACHADO);
        assertThat(pedidos.bloquearPorId(order)).isPresent();
        var loadedLine = detallesPedido.findByPedidoIdOrderByIdAsc(order).getFirst();
        assertThat(loadedLine.getId()).isEqualTo(orderLine);
        assertThat(loadedLine.pendiente()).isEqualByComparingTo("2");
        assertThat(loadedLine.lineaComercial().precioUnitario()).isEqualByComparingTo("10.00");
        assertThat(despachos.findByClaveIdempotencia(key).orElseThrow().getEstado()).isEqualTo(EstadoDespacho.BORRADOR);
        assertThat(despachos.bloquearPorId(dispatch)).isPresent();
        assertThat(detallesDespacho.findByDespachoIdOrderByIdAsc(dispatch)).extracting(DetalleDespacho::getId).containsExactly(dispatchLine);
        assertThat(detallesDespacho.findById(dispatchLine).orElseThrow().getCostoUnitario()).isNull();
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,product)).isZero();
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,product)).isZero();
        // Synthetic source only: no stock application or delivery confirmation is performed.
        db.update("update detalles_despacho set costo_unitario=1.2345 where id=?", dispatchLine);
        long movement = id("insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_despacho_id,clave_operacion) values (?,?,'SALIDA','DESPACHO_VENTA',1,1.2345,?,?,?) returning id",product,warehouse,user,dispatchLine,"DES:" + key);
        em.clear();
        var loadedMovement = movements.findById(movement).orElseThrow();
        assertThat(loadedMovement.getDetalleDespachoId()).isEqualTo(dispatchLine);
        assertThat(loadedMovement.getDetalleRecepcionId()).isNull();
        assertThat(loadedMovement.getDetalleAjusteId()).isNull();
        assertThat(loadedMovement.getCostoUnitario()).isEqualByComparingTo("1.2345");
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,product)).isZero();
    }
}
