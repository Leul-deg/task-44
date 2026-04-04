package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.JobPostingHistory;
import com.shiftworks.jobops.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPostingHistoryRepository extends JpaRepository<JobPostingHistory, Long> {

    List<JobPostingHistory> findByJobPosting_IdOrderByCreatedAtDesc(Long jobPostingId);

    Optional<JobPostingHistory> findTop1ByJobPosting_IdAndSnapshotJsonIsNotNullOrderByCreatedAtDesc(Long jobPostingId);

    List<JobPostingHistory> findByJobPosting_IdAndNewStatusOrderByCreatedAtDesc(Long jobPostingId, JobStatus newStatus);
}
