package com.shiftworks.jobops.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.JobPostingHistory;
import com.shiftworks.jobops.entity.JobPostingTag;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.repository.JobPostingHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JobHistoryService {

    private final JobPostingHistoryRepository jobPostingHistoryRepository;
    private final ObjectMapper objectMapper;

    public void record(JobPosting jobPosting, JobStatus previousStatus, JobStatus newStatus, User actor, String reason) {
        JobPostingHistory history = new JobPostingHistory();
        history.setJobPosting(jobPosting);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(actor);
        history.setChangeReason(reason);
        history.setSnapshotJson(serializeSnapshot(jobPosting));
        jobPostingHistoryRepository.save(history);
    }

    public JobPostingHistory findLatestSnapshot(Long jobPostingId) {
        return jobPostingHistoryRepository.findTop1ByJobPosting_IdAndSnapshotJsonIsNotNullOrderByCreatedAtDesc(jobPostingId)
            .orElse(null);
    }

    public Map<String, Object> readSnapshot(JobPostingHistory history) {
        if (history == null || history.getSnapshotJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(history.getSnapshotJson(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            return Map.of();
        }
    }

    private String serializeSnapshot(JobPosting jobPosting) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("title", jobPosting.getTitle());
        snapshot.put("description", jobPosting.getDescription());
        snapshot.put("status", jobPosting.getStatus());
        snapshot.put("category", jobPosting.getCategory().getName());
        snapshot.put("location", jobPosting.getLocation().getCity() + ", " + jobPosting.getLocation().getState());
        snapshot.put("payAmount", jobPosting.getPayAmount());
        snapshot.put("payType", jobPosting.getPayType());
        snapshot.put("settlementType", jobPosting.getSettlementType());
        snapshot.put("headcount", jobPosting.getHeadcount());
        snapshot.put("weeklyHours", jobPosting.getWeeklyHours());
        snapshot.put("validityStart", jobPosting.getValidityStart());
        snapshot.put("validityEnd", jobPosting.getValidityEnd());
        snapshot.put("tags", jobPosting.getTags().stream().map(JobPostingTag::getTagName).toList());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
