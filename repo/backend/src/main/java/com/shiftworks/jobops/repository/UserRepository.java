package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findByStatus(UserStatus status);

    boolean existsByRole(UserRole role);
}
