package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.Appeal;
import com.shiftworks.jobops.enums.AppealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AppealRepository extends JpaRepository<Appeal, Long>, JpaSpecificationExecutor<Appeal> {

    List<Appeal> findByStatus(AppealStatus status);

    List<Appeal> findByEmployer_Id(Long employerId);

    boolean existsByJobPosting_IdAndStatus(Long jobPostingId, AppealStatus status);
}
