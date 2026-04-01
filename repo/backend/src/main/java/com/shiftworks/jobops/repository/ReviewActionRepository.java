package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.ReviewAction;
import com.shiftworks.jobops.enums.ReviewActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReviewActionRepository extends JpaRepository<ReviewAction, Long> {

    List<ReviewAction> findByReviewer_Id(Long reviewerId);

    List<ReviewAction> findByJobPosting_IdOrderByCreatedAtDesc(Long jobPostingId);

    List<ReviewAction> findTop10ByReviewer_IdOrderByCreatedAtDesc(Long reviewerId);

    long countByReviewer_IdAndCreatedAtBetween(Long reviewerId, Instant start, Instant end);

    Optional<ReviewAction> findTop1ByJobPosting_IdAndActionOrderByCreatedAtDesc(Long jobPostingId, ReviewActionType action);

    @Query(value = "SELECT ra.reviewer_id, u.username, DATE(ra.created_at) AS date_value, COUNT(*) AS count_value FROM review_actions ra JOIN users u ON u.id = ra.reviewer_id WHERE ra.created_at >= :from AND ra.created_at < :to GROUP BY ra.reviewer_id, DATE(ra.created_at) ORDER BY DATE(ra.created_at)", nativeQuery = true)
    List<Object[]> findReviewerActivityBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT AVG(TIMESTAMPDIFF(HOUR, jp.created_at, ra.created_at)) FROM review_actions ra JOIN job_postings jp ON jp.id = ra.job_posting_id WHERE ra.created_at >= :from AND ra.created_at < :to", nativeQuery = true)
    Double findAverageHandlingHours(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT SUM(CASE WHEN action = 'APPROVE' THEN 1 ELSE 0 END) AS approved, COUNT(*) AS total FROM review_actions WHERE created_at >= :from AND created_at < :to", nativeQuery = true)
    Object[] findApprovalStats(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT DATE(created_at) AS date_value, COUNT(*) AS count_value FROM review_actions WHERE action = 'TAKEDOWN' AND created_at >= :from AND created_at < :to GROUP BY DATE(created_at) ORDER BY DATE(created_at)", nativeQuery = true)
    List<Object[]> findTakedownTrendBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(r) FROM ReviewAction r WHERE r.action = 'TAKEDOWN' AND FUNCTION('DATE', r.createdAt) = CURRENT_DATE")
    long countTodayTakedowns();

    @Query(value = "SELECT DATE(created_at) AS d, COUNT(*) AS cnt FROM review_actions WHERE action='TAKEDOWN' AND created_at >= CURDATE() - INTERVAL 30 DAY AND DATE(created_at) < CURDATE() GROUP BY DATE(created_at)", nativeQuery = true)
    List<Object[]> dailyTakedownsLast30Days();

    @Query(value = "SELECT COUNT(CASE WHEN action='APPROVE' THEN 1 END), COUNT(*) FROM review_actions WHERE DATE(created_at) = CURDATE()", nativeQuery = true)
    Object[] todayApprovalCounts();

    @Query(value = "SELECT DATE(created_at) AS d, SUM(CASE WHEN action='APPROVE' THEN 1 ELSE 0 END) AS approved, COUNT(*) AS total FROM review_actions WHERE created_at >= CURDATE() - INTERVAL 30 DAY AND DATE(created_at) < CURDATE() GROUP BY DATE(created_at)", nativeQuery = true)
    List<Object[]> dailyApprovalStatsLast30Days();
}
