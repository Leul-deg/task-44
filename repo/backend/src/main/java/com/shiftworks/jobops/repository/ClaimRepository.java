package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.Claim;
import com.shiftworks.jobops.enums.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    long countByStatus(ClaimStatus status);

    @Query("SELECT COUNT(c) FROM Claim c WHERE FUNCTION('DATE', c.createdAt) = CURRENT_DATE")
    long countTodayClaims();

    @Query(value = "SELECT DATE(created_at) AS d, COUNT(*) AS cnt FROM claims WHERE created_at >= CURDATE() - INTERVAL 30 DAY AND DATE(created_at) < CURDATE() GROUP BY DATE(created_at)", nativeQuery = true)
    List<Object[]> dailyClaimsLast30Days();
}
