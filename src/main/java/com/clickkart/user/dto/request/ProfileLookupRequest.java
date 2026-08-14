// src/main/java/com/clickkart/user/dto/request/ProfileLookupRequest.java
package com.clickkart.user.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Bulk profile resolution for service-to-service callers.
 *
 * <p>Exists so an admin order list showing 50 customers does not become 50 cross-service calls.
 * POST rather than GET with a repeated query parameter: a list of user identifiers in a URL would
 * land in access logs, proxy logs and browser history, which is exactly where identifiers should
 * not accumulate.
 *
 * <p>The size cap is a guard, not a business rule - an uncapped list lets one request pull the
 * entire user table in a single query.
 */
public record ProfileLookupRequest(
        @NotEmpty(message = "must contain at least one id")
                @Size(max = 200, message = "must contain at most 200 ids")
                List<String> userPublicIds) {}
