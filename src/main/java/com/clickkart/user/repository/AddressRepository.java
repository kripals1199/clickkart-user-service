// src/main/java/com/clickkart/user/repository/AddressRepository.java
package com.clickkart.user.repository;

import com.clickkart.user.entity.AddressEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every finder filters {@code deleted = false}. There is deliberately no plain
 * {@code findById} in use anywhere in the service layer - see {@link
 * #findByIdAndProfileUserPublicIdAndDeletedFalse}, which folds the ownership check into the query
 * itself so a caller cannot forget it.
 */
public interface AddressRepository extends JpaRepository<AddressEntity, Long> {

    List<AddressEntity> findByProfileUserPublicIdAndDeletedFalseOrderByDefaultAddressDescIdAsc(String userPublicId);

    /**
     * The single lookup used by every self-service read and write path.
     *
     * <p>Scoping by owner <em>inside the query</em> rather than loading by id and comparing
     * afterwards is what makes the "not found" and "belongs to someone else" cases indistinguishable
     * from outside: both return empty, both surface as 404. A 403 here would confirm to an attacker
     * that the id exists, turning this endpoint into an oracle for enumerating other customers' rows.
     */
    Optional<AddressEntity> findByIdAndProfileUserPublicIdAndDeletedFalse(Long id, String userPublicId);

    long countByProfileUserPublicIdAndDeletedFalse(String userPublicId);

    /**
     * Demotes every other address of this profile in one statement.
     *
     * <p>A read-modify-write loop over the address list would be the obvious alternative, but it
     * leaves a window where two concurrent "set default" requests each see the other's row as
     * not-yet-default and both commit, leaving a profile with two defaults. Doing it as a single
     * bulk UPDATE, followed by promoting the target row, keeps the invariant enforced by the
     * database rather than by request timing.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AddressEntity a set a.defaultAddress = false "
            + "where a.profile.userPublicId = :userPublicId and a.id <> :keepId and a.defaultAddress = true")
    int clearDefaultForOtherAddresses(@Param("userPublicId") String userPublicId, @Param("keepId") Long keepId);
}
