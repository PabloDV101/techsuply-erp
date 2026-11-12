package com.techsupply.erp_backend.audit.infrastructure.persistence;

import com.techsupply.erp_backend.audit.domain.model.AuditEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Used only for denials and post-commit security outcomes. */
@Service
public class IndependentAuditWriter {
    private final AuditEventRepository repository;

    public IndependentAuditWriter(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(AuditEvent event) {
        repository.saveAndFlush(event);
    }
}
