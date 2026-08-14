// src/main/java/com/clickkart/user/repository/SellerProfileRepository.java
package com.clickkart.user.repository;

import com.clickkart.user.entity.SellerProfileEntity;
import com.clickkart.user.enums.SellerVerificationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfileEntity, Long> {

    Optional<SellerProfileEntity> findByProfileUserPublicId(String userPublicId);

    boolean existsByGstin(String gstin);

    Optional<SellerProfileEntity> findByGstin(String gstin);

    /** Operator work queue - sellers awaiting a verification decision. */
    Page<SellerProfileEntity> findByVerificationStatus(SellerVerificationStatus status, Pageable pageable);

    /** Used to null out a pickup address when the underlying address row is soft-deleted. */
    List<SellerProfileEntity> findByPickupAddressId(Long pickupAddressId);
}
