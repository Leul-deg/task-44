package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    List<UserSession> findByUser_Id(Long userId);

    List<UserSession> findByValidTrue();
}
