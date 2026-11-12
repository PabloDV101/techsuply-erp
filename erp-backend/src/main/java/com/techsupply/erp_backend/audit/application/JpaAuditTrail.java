package com.techsupply.erp_backend.audit.application;

import com.techsupply.erp_backend.audit.domain.model.AuditActorType;
import com.techsupply.erp_backend.audit.domain.model.AuditEvent;
import com.techsupply.erp_backend.audit.domain.model.AuditResult;
import com.techsupply.erp_backend.audit.infrastructure.persistence.AuditEventRepository;
import com.techsupply.erp_backend.audit.infrastructure.persistence.IndependentAuditWriter;
import java.time.Clock;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class JpaAuditTrail implements AuditTrail {
    private static final Logger log = LoggerFactory.getLogger(JpaAuditTrail.class);
    private final AuditActorProvider actors;
    private final AuditEventRepository repository;
    private final IndependentAuditWriter independent;
    private final Clock clock;

    public JpaAuditTrail(AuditActorProvider actors, AuditEventRepository repository,
            IndependentAuditWriter independent, Clock clock) {
        this.actors = actors;
        this.repository = repository;
        this.independent = independent;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void change(AuditAction action, Long resourceId, Set<AuditField> fields,
            AuditState previousState, AuditState newState) {
        if (action == AuditAction.LOGIN_SUCCEEDED || action == AuditAction.LOGIN_DENIED
                || action == AuditAction.LOGOUT || action == AuditAction.AUTHORIZATION_DENIED
                || action == AuditAction.ADMIN_BOOTSTRAPPED) {
            throw new IllegalArgumentException("Acción reservada para el flujo de seguridad");
        }
        Long actorId = actors.currentUserId().orElseThrow(() ->
            new IllegalStateException("Un cambio protegido requiere actor autenticado"));
        repository.saveAndFlush(event(action, AuditActorType.USUARIO, actorId, resourceId,
            AuditResult.EXITO, null, fields, previousState, newState));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void verifiedLogin(Long userId, Long sessionId) {
        repository.saveAndFlush(event(AuditAction.LOGIN_SUCCEEDED, AuditActorType.USUARIO, userId,
            sessionId, AuditResult.EXITO, null, Set.of(), null, null));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void systemBootstrap(Long userId) {
        repository.saveAndFlush(event(AuditAction.ADMIN_BOOTSTRAPPED, AuditActorType.SISTEMA, null,
            userId, AuditResult.EXITO, null, Set.of(), null, null));
    }

    @Override
    public void deniedLoginAfterCommit() {
        afterCommit(event(AuditAction.LOGIN_DENIED, AuditActorType.ANONIMO, null,
            null, AuditResult.DENEGADO, "CREDENTIALS_REJECTED", Set.of(), null, null));
    }

    @Override
    public void logoutAfterCommit(Long userId, Long sessionId) {
        afterCommit(event(AuditAction.LOGOUT, AuditActorType.USUARIO, userId,
            sessionId, AuditResult.EXITO, null, Set.of(), null, null));
    }

    @Override
    public void deniedAuthorization() {
        Long actorId = actors.currentUserId().orElse(null);
        bestEffort(event(AuditAction.AUTHORIZATION_DENIED,
            actorId == null ? AuditActorType.ANONIMO : AuditActorType.USUARIO, actorId,
            null, AuditResult.DENEGADO, "PERMISSION_DENIED", Set.of(), null, null));
    }

    private void afterCommit(AuditEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("El evento posterior requiere transacción activa");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { bestEffort(event); }
        });
    }

    private void bestEffort(AuditEvent event) {
        try {
            independent.append(event);
        } catch (RuntimeException ex) {
            // Do not leak SQL, credentials, tokens or request data into this log.
            log.warn("No se pudo guardar el evento de auditoría {}", event.getAccion());
        }
    }

    private AuditEvent event(AuditAction action, AuditActorType actorType, Long userId, Long resourceId,
            AuditResult result, String reason, Set<AuditField> fields, AuditState before, AuditState after) {
        return new AuditEvent(action, actorType, userId, resourceId, result, reason, fields, before, after,
            clock.instant());
    }
}
