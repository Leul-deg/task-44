package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.JobPostingTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobPostingTagRepository extends JpaRepository<JobPostingTag, Long> {

    List<JobPostingTag> findByJobPosting_Id(Long jobPostingId);
}
