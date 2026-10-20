package com.techsupply.erp_backend.notifications.domain.model;

import com.techsupply.erp_backend.notifications.application.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notificaciones")
public class InternalNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 40)
    private NotificationType tipoEvento;
    @Column(nullable = false, length = 40)
    private String modulo;
    @Column(nullable = false, length = 80)
    private String entidad;
    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;
    @Column(name = "clave_operacion", nullable = false, length = 120)
    private String claveOperacion;
    @Column(nullable = false, length = 15)
    private String canal;
    @Column(nullable = false, length = 200)
    private String asunto;
    @Column(nullable = false, columnDefinition = "text")
    private String cuerpo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private NotificationStatus estado;
    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;
    @Column(name = "leida_en")
    private Instant leidaEn;

    protected InternalNotification() {}

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public NotificationType getTipoEvento() { return tipoEvento; }
    public String getModulo() { return modulo; }
    public String getEntidad() { return entidad; }
    public Long getEntidadId() { return entidadId; }
    public String getAsunto() { return asunto; }
    public String getCuerpo() { return cuerpo; }
    public NotificationStatus getEstado() { return estado; }
    public Instant getCreadaEn() { return creadaEn; }
    public Instant getLeidaEn() { return leidaEn; }
}
