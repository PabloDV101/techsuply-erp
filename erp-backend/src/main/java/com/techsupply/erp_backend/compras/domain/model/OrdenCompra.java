package com.techsupply.erp_backend.compras.domain.model;

import com.techsupply.erp_backend.maestros.domain.model.Departamento;
import com.techsupply.erp_backend.maestros.domain.model.Proveedor;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "ordenes_compra")
public class OrdenCompra {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30, unique = true)
    private String folio;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "departamento_id", nullable = false)
    private Departamento departamento;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "creada_por_usuario_id", nullable = false)
    private Usuario creadaPor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "aprobada_por_usuario_id")
    private Usuario aprobadaPor;
    @Column(name = "aprobada_en") private Instant aprobadaEn;
    @Column(name = "motivo_rechazo", columnDefinition = "text") private String motivoRechazo;
    @Column(nullable = false) private Instant fecha;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 25) private EstadoOrdenCompra estado;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal impuestos = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal total = BigDecimal.ZERO;
    @Column(columnDefinition = "text") private String observaciones;
    @OneToMany(mappedBy = "orden", cascade = CascadeType.PERSIST)
    private List<DetalleOrdenCompra> detalles = new ArrayList<>();

    protected OrdenCompra() {}
    public OrdenCompra(String folio, Proveedor proveedor, Departamento departamento, Usuario creadaPor, Instant fecha, String observaciones) {
        if (folio == null || folio.isBlank() || folio.length() > 30) throw new IllegalArgumentException("Folio inválido");
        this.folio = folio; this.proveedor = Objects.requireNonNull(proveedor); this.departamento = Objects.requireNonNull(departamento);
        this.creadaPor = Objects.requireNonNull(creadaPor); this.fecha = Objects.requireNonNull(fecha);
        this.observaciones = observaciones; this.estado = EstadoOrdenCompra.BORRADOR;
    }
    public void agregarDetalle(DetalleOrdenCompra detalle) {
        exigirEstado(EstadoOrdenCompra.BORRADOR);
        Objects.requireNonNull(detalle);
        if (detalle.getOrden() != null) throw new IllegalArgumentException("Detalle ya asignado");
        if (detalles.stream().anyMatch(d -> Objects.equals(d.getProducto().getId(), detalle.getProducto().getId())))
            throw new IllegalArgumentException("Producto duplicado");
        List<CalculoOrden.Linea> lineas = new ArrayList<>(detalles.stream().map(DetalleOrdenCompra::linea).toList());
        lineas.add(detalle.linea());
        CalculoOrden.Totales calculo = CalculoOrden.calcular(lineas);
        detalle.asignarOrden(this); detalles.add(detalle);
        subtotal = calculo.subtotal(); impuestos = calculo.impuestos(); total = calculo.total();
    }
    public void enviarAprobacion() {
        exigirEstado(EstadoOrdenCompra.BORRADOR);
        if (detalles.isEmpty()) throw new IllegalStateException("La orden debe tener líneas");
        estado = EstadoOrdenCompra.PENDIENTE_APROBACION;
    }
    public void aprobar(Usuario actor, Instant instante) {
        exigirEstado(EstadoOrdenCompra.PENDIENTE_APROBACION);
        Objects.requireNonNull(actor); Objects.requireNonNull(instante);
        if (actor.getId() == null || creadaPor.getId() == null || actor.getId().equals(creadaPor.getId()))
            throw new IllegalArgumentException("El aprobador debe ser distinto del creador");
        CalculoOrden.Totales calculo = CalculoOrden.calcular(detalles.stream().map(DetalleOrdenCompra::linea).toList());
        if (subtotal.compareTo(calculo.subtotal()) != 0 || impuestos.compareTo(calculo.impuestos()) != 0
                || total.compareTo(calculo.total()) != 0) throw new IllegalStateException("Totales inconsistentes");
        aprobadaPor = actor; aprobadaEn = instante; estado = EstadoOrdenCompra.APROBADA;
    }
    public void registrarEnvio() { exigirEstado(EstadoOrdenCompra.APROBADA); estado = EstadoOrdenCompra.ENVIADA; }
    public void registrarRecepcion(boolean completa, boolean hayAceptadas) {
        if (estado != EstadoOrdenCompra.ENVIADA && estado != EstadoOrdenCompra.PARCIALMENTE_RECIBIDA)
            throw new IllegalStateException("Orden no admite recepción");
        estado = completa ? EstadoOrdenCompra.RECIBIDA : hayAceptadas ? EstadoOrdenCompra.PARCIALMENTE_RECIBIDA : EstadoOrdenCompra.ENVIADA;
    }
    private void exigirEstado(EstadoOrdenCompra esperado) {
        if (estado != esperado) throw new IllegalStateException("Transición de orden inválida");
    }
    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public Proveedor getProveedor() { return proveedor; }
    public Departamento getDepartamento() { return departamento; }
    public Usuario getCreadaPor() { return creadaPor; }
    public Usuario getAprobadaPor() { return aprobadaPor; }
    public Instant getAprobadaEn() { return aprobadaEn; }
    public String getMotivoRechazo() { return motivoRechazo; }
    public Instant getFecha() { return fecha; }
    public EstadoOrdenCompra getEstado() { return estado; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getImpuestos() { return impuestos; }
    public BigDecimal getTotal() { return total; }
    public String getObservaciones() { return observaciones; }
    public List<DetalleOrdenCompra> getDetalles() { return List.copyOf(detalles); }
}
