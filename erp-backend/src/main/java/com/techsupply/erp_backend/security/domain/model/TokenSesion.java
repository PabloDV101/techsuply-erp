package com.techsupply.erp_backend.security.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "tokens_sesion", uniqueConstraints = @UniqueConstraint(columnNames = "token_hash"))
public class TokenSesion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "emitido_en", nullable = false)
    private Instant emitidoEn;

    @Column(name = "vence_en", nullable = false)
    private Instant venceEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    protected TokenSesion() {}
    public TokenSesion(Usuario usuario, String tokenHash, Instant emitidoEn, Instant venceEn) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.emitidoEn = emitidoEn;
        this.venceEn = venceEn;
    }
    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Instant getEmitidoEn() { return emitidoEn; }
    public Instant getVenceEn() { return venceEn; }
    public Instant getRevocadoEn() { return revocadoEn; }
    public void setRevocadoEn(Instant revocadoEn) { this.revocadoEn = revocadoEn; }
}
