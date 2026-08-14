// src/main/java/com/clickkart/user/repository/UserProfileRepository.java
package com.clickkart.user.repository;

import com.clickkart.user.entity.UserProfileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserProfileRepository
        extends JpaRepository<UserProfileEntity, Long>, JpaSpecificationExecutor<UserProfileEntity> {

    Optional<UserProfileEntity> findByUserPublicId(String userPublicId);

    boolean existsByUserPublicId(String userPublicId);
}
