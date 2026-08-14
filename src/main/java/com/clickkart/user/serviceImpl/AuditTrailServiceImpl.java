// src/main/java/com/clickkart/user/serviceImpl/AuditTrailServiceImpl.java
package com.clickkart.user.serviceImpl;

import com.clickkart.user.constant.LoggerNames;
import com.clickkart.user.enums.UserAuditAction;
import com.clickkart.user.feign.AuditEventRequest;
import com.clickkart.user.feign.AuditLogServiceClient;
import com.clickkart.user.service.AuditTrailService;
import com.clickkart.user.web.RequestMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Thin dispatcher to the Audit Log Service. There is no local audit table here - the whole point of
 * the central hash-chained trail is that it is one tamper-evident sequence, not per-service copies
 * that can disagree.
 *
 * <p>{@code details} must never carry personal data. The audit trail is queryable by operators and
 * is retained far longer than the profile row itself, so recording the <em>shape</em> of a change
 * (which fields, how many addresses) rather than its content keeps a customer's home address out of
 * a second, longer-lived store. Callers below follow that rule.
 */
@Slf4j(topic = LoggerNames.AUDIT)
@Service
@RequiredArgsConstructor
public class AuditTrailServiceImpl implements AuditTrailService {

    private final AuditLogServiceClient auditLogServiceClient;

    @Override
    public void record(
            String correlationId,
            String actor,
            UserAuditAction action,
            RequestMetadata requestMetadata,
            String details) {
        AuditEventRequest request =
                AuditEventRequest.of(correlationId, actor, action, requestMetadata.ipAddress(), details);
        auditLogServiceClient.logEvent(correlationId, request);
        log.info("AUDIT_DISPATCHED correlationId={} actor={} action={} details={}", correlationId, actor, action, details);
    }
}
