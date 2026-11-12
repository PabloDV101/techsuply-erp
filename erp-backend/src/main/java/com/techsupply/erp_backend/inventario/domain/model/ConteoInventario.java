package com.techsupply.erp_backend.inventario.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity @Table(name = "conteos_inventario")
public class ConteoInventario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30, unique = true) private String folio;
    @Column(name = "almacen_id", nullable = false) private Long almacenId;
    @Column(name = "creado_por_usuario_id", nullable = false) private Long creadoPorUsuarioId;
    @Column(name = "fecha_programada", nullable = false) private LocalDate fechaProgramada;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private EstadoConteo estado;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private ClaseConteo clase;
    @OneToMany(mappedBy = "conteo", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<DetalleConteo> detalles = new ArrayList<>();

    protected ConteoInventario() {}
    public ConteoInventario(String folio, Long almacenId, Long creadoPorUsuarioId, LocalDate fechaProgramada, ClaseConteo clase) {
        this.folio = texto(folio, 30); this.almacenId = identificador(almacenId);
        this.creadoPorUsuarioId = identificador(creadoPorUsuarioId); this.fechaProgramada = Objects.requireNonNull(fechaProgramada);
        this.clase = Objects.requireNonNull(clase); this.estado = EstadoConteo.PROGRAMADO;
    }
    public void capturar(Long productoId, BigDecimal sistema, BigDecimal contada, boolean existenciaPrevia, Integer versionExistencia) {
        if (estado != EstadoConteo.PROGRAMADO && estado != EstadoConteo.EN_CAPTURA) throw new IllegalStateException("Conteo no admite captura");
        identificador(productoId);
        DetalleConteo existente = detalles.stream().filter(d -> d.getProductoId().equals(productoId)).findFirst().orElse(null);
        if (existente == null) {
            DetalleConteo nuevo = new DetalleConteo(this, productoId, sistema, contada, existenciaPrevia, versionExistencia);
            detalles.add(nuevo);
        } else existente.recapturar(sistema, contada, existenciaPrevia, versionExistencia);
        estado = EstadoConteo.EN_CAPTURA;
    }
    public void cerrar() {
        if (estado != EstadoConteo.EN_CAPTURA || detalles.isEmpty()) throw new IllegalStateException("Conteo sin captura o estado inválido");
        estado = EstadoConteo.CERRADO;
    }
    public void cancelar() {
        if (estado != EstadoConteo.PROGRAMADO && estado != EstadoConteo.EN_CAPTURA) throw new IllegalStateException("Conteo no admite cancelación");
        estado = EstadoConteo.CANCELADO;
    }
    static Long identificador(Long id) { if (id == null || id < 1) throw new IllegalArgumentException("Identificador inválido"); return id; }
    static String texto(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("Texto inválido");
        return value.trim();
    }
    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public Long getAlmacenId() { return almacenId; }
    public Long getCreadoPorUsuarioId() { return creadoPorUsuarioId; }
    public LocalDate getFechaProgramada() { return fechaProgramada; }
    public EstadoConteo getEstado() { return estado; }
    public ClaseConteo getClase() { return clase; }
    public List<DetalleConteo> getDetalles() { return List.copyOf(detalles); }
}
