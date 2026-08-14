// src/main/java/com/clickkart/user/repository/UserProfileSpecifications.java
package com.clickkart.user.repository;

import com.clickkart.user.entity.UserProfileEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/** Criteria used by the admin browse endpoint. */
public final class UserProfileSpecifications {

    private UserProfileSpecifications() {}

    /**
     * Case-insensitive contains-match across the three name fields and the public id.
     *
     * <p>The term is escaped before being wrapped in wildcards: without that, a search for
     * {@code %} would match every row and {@code _} would match any single character, letting an
     * admin-facing search accidentally (or deliberately) become a full table scan with a
     * user-controlled pattern.
     */
    public static Specification<UserProfileEntity> matchesSearchTerm(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLikeWildcards(search.trim().toLowerCase()) + "%";
        return (root, query, builder) -> {
            Predicate byDisplayName = builder.like(builder.lower(root.get("displayName")), pattern, '\\');
            Predicate byFirstName = builder.like(builder.lower(root.get("firstName")), pattern, '\\');
            Predicate byLastName = builder.like(builder.lower(root.get("lastName")), pattern, '\\');
            Predicate byPublicId = builder.like(builder.lower(root.get("userPublicId")), pattern, '\\');
            return builder.or(byDisplayName, byFirstName, byLastName, byPublicId);
        };
    }

    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
