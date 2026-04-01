package com.shiftworks.jobops.entity;

import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.PayType;
import com.shiftworks.jobops.enums.SettlementType;
import com.shiftworks.jobops.converter.EncryptedStringConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "job_postings")
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private User employer;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_type", nullable = false)
    private PayType payType;

    @Column(name = "pay_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal payAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false)
    private SettlementType settlementType;

    @Column(nullable = false)
    private int headcount;

    @Column(name = "weekly_hours", nullable = false, precision = 4, scale = 1)
    private BigDecimal weeklyHours;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact_phone", length = 500)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.DRAFT;

    @Column(name = "validity_start")
    private LocalDate validityStart;

    @Column(name = "validity_end")
    private LocalDate validityEnd;

    @Column(name = "reviewer_notes", columnDefinition = "TEXT")
    private String reviewerNotes;

    @Column(name = "takedown_reason", columnDefinition = "TEXT")
    private String takedownReason;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<JobPostingTag> tags = new ArrayList<>();
}
