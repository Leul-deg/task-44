package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long>, JpaSpecificationExecutor<JobPosting> {

    List<JobPosting> findTop5ByEmployer_IdOrderByCreatedAtDesc(Long employerId);

    long countByEmployer_Id(Long employerId);

    long countByEmployer_IdAndStatus(Long employerId, JobStatus status);

    Optional<JobPosting> findByIdAndEmployer_Id(Long id, Long employerId);

    long countByCategory_IdAndStatusIn(Long categoryId, List<JobStatus> statuses);

    long countByLocation_IdAndStatusIn(Long locationId, List<JobStatus> statuses);

    @Query(value = "SELECT DATE(created_at) AS date_value, COUNT(*) AS count_value FROM job_postings WHERE created_at >= :from AND created_at < :to GROUP BY DATE(created_at) ORDER BY DATE(created_at)", nativeQuery = true)
    List<Object[]> findPostVolumeBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT status, COUNT(*) FROM job_postings GROUP BY status", nativeQuery = true)
    List<Object[]> findPostStatusCounts();

    @Query(value = "SELECT status, COUNT(*) FROM job_postings WHERE created_at >= :from AND created_at < :to GROUP BY status", nativeQuery = true)
    List<Object[]> findPostStatusCountsBetween(@Param("from") Instant from, @Param("to") Instant to);

    long countByStatus(JobStatus status);

    @Query("SELECT COUNT(j) FROM JobPosting j WHERE j.createdAt >= :since")
    long countCreatedSince(@Param("since") Instant since);

    /** Returns the raw (encrypted) contact_phone column, bypassing the JPA converter. Test-only use. */
    @Query(value = "SELECT contact_phone FROM job_postings WHERE id = :id", nativeQuery = true)
    String findRawContactPhoneById(@Param("id") Long id);
}
