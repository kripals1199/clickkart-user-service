// src/test/java/com/clickkart/user/repository/UserProfileSpecificationsTest.java
package com.clickkart.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.user.entity.UserProfileEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class UserProfileSpecificationsTest {

    @Test
    void aBlankSearchMatchesEverythingRatherThanNothing() {
        // Returning null hands Spring Data an unfiltered query - the correct "no filter applied"
        // signal. Returning an always-false predicate instead would make an empty search box
        // silently show zero customers.
        assertThat(UserProfileSpecifications.matchesSearchTerm(null)).isNull();
        assertThat(UserProfileSpecifications.matchesSearchTerm("   ")).isNull();
    }

    @Test
    void likeWildcardsInTheSearchTermAreEscapedSoTheyMatchLiterally() {
        // Unescaped, a search for "%" would match every row and "_" any single character, turning
        // an operator search box into a user-controlled full table scan.
        @SuppressWarnings("unchecked")
        Root<UserProfileEntity> root = mock(Root.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        Path<String> path = mock(Path.class);
        @SuppressWarnings("unchecked")
        Expression<String> lowered = mock(Expression.class);

        when(root.<String>get(org.mockito.ArgumentMatchers.anyString())).thenReturn(path);
        when(builder.lower(path)).thenReturn(lowered);

        Specification<UserProfileEntity> specification = UserProfileSpecifications.matchesSearchTerm("50%_off");
        specification.toPredicate(root, null, builder);

        verify(builder, org.mockito.Mockito.atLeastOnce())
                .like(eq(lowered), eq("%50\\%\\_off%"), anyChar());
    }

    @Test
    void theSearchTermIsLoweredAndTrimmedSoMatchingIsCaseInsensitive() {
        @SuppressWarnings("unchecked")
        Root<UserProfileEntity> root = mock(Root.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        Path<String> path = mock(Path.class);
        @SuppressWarnings("unchecked")
        Expression<String> lowered = mock(Expression.class);

        when(root.<String>get(org.mockito.ArgumentMatchers.anyString())).thenReturn(path);
        when(builder.lower(path)).thenReturn(lowered);

        UserProfileSpecifications.matchesSearchTerm("  AsHa  ").toPredicate(root, null, builder);

        verify(builder, org.mockito.Mockito.atLeastOnce()).like(eq(lowered), eq("%asha%"), anyChar());
    }
}
