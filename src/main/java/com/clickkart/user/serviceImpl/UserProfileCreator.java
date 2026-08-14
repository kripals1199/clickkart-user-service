// src/main/java/com/clickkart/user/serviceImpl/UserProfileCreator.java
package com.clickkart.user.serviceImpl;

import com.clickkart.user.entity.UserProfileEntity;
import com.clickkart.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts a new profile row in its <em>own</em> transaction.
 *
 * <p>This exists as a separate bean for two reasons, both of which are easy to get wrong:
 *
 * <ol>
 *   <li><strong>A failed flush poisons the transaction it happens in.</strong> When two concurrent
 *       first-time requests race, one loses the unique constraint on {@code user_public_id}.
 *       Hibernate marks the transaction rollback-only at that point, so "catch the violation and
 *       re-read the winner's row" does not work if the insert and the re-read share a transaction -
 *       the re-read fails too, and the caller gets a 500 instead of the profile. Committing the
 *       insert in a suspended inner transaction keeps the caller's transaction clean and usable.
 *   <li><strong>{@code REQUIRES_NEW} is ignored on self-invocation.</strong> Spring's transaction
 *       support is proxy-based, so calling a {@code REQUIRES_NEW} method from another method of the
 *       same class silently runs it in the caller's transaction. The propagation would appear to be
 *       configured while doing nothing at all. Same reason {@code AuthFailureRecorder} and {@code
 *       NotificationFailureRecorder} are their own beans elsewhere in this platform.
 * </ol>
 *
 * <p>Consequence worth being explicit about: the profile row commits independently, so it survives
 * even if the caller's transaction later rolls back. That is deliberate and harmless - the row is an
 * empty shell containing no customer data, and the next request simply finds it instead of creating
 * it. The alternative, a hard failure on a routine concurrent first page-load, is far worse.
 */
@Component
@RequiredArgsConstructor
public class UserProfileCreator {

    private final UserProfileRepository userProfileRepository;

    /**
     * @throws org.springframework.dao.DataIntegrityViolationException if another request created
     *     this profile first - the caller is expected to treat that as "already exists" and re-read
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createInNewTransaction(String userPublicId, String defaultLanguage, String defaultCurrency) {
        userProfileRepository.saveAndFlush(
                UserProfileEntity.createFor(userPublicId, defaultLanguage, defaultCurrency));
    }
}
