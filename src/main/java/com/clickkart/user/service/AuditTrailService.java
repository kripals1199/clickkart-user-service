// src/main/java/com/clickkart/user/service/AuditTrailService.java
package com.clickkart.user.service;

import com.clickkart.user.enums.UserAuditAction;
import com.clickkart.user.web.RequestMetadata;

/** Reports one event to the central Audit Log Service. Throws if the sink is unreachable - see the fallback factory. */
public interface AuditTrailService {

    void record(
            String correlationId,
            String actor,
            UserAuditAction action,
            RequestMetadata requestMetadata,
            String details);
}
