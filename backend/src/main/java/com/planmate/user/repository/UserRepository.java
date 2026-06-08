package com.planmate.user.repository;

import com.planmate.user.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    boolean existsByEmailCanonical(String emailCanonical);

    Optional<UserEntity> findByEmailCanonical(String emailCanonical);

}
