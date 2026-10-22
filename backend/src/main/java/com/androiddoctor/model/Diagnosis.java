package com.androiddoctor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagnoses")
public class Diagnosis {
    @Id
    private UUID id;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(length = 255)
    private String sourceName;
    @Column(nullable = false, length = 64)
    private String category;
    @Column(nullable = false, length = 16)
    private String severity;
    @Column(nullable = false, length = 600)
    private String summary;
    @Lob
    @Column(nullable = false)
    private String sanitizedLog;
    @Lob
    @Column(nullable = false)
    private String findingsJson;
    @Lob
    private String aiAnalysis;
    private boolean aiUsed;
    private int redactionCount;

    protected Diagnosis() {}

    public Diagnosis(UUID id, Instant createdAt, String sourceName, String category, String severity,
                     String summary, String sanitizedLog, String findingsJson, String aiAnalysis,
                     boolean aiUsed, int redactionCount) {
        this.id = id;
        this.createdAt = createdAt;
        this.sourceName = sourceName;
        this.category = category;
        this.severity = severity;
        this.summary = summary;
        this.sanitizedLog = sanitizedLog;
        this.findingsJson = findingsJson;
        this.aiAnalysis = aiAnalysis;
        this.aiUsed = aiUsed;
        this.redactionCount = redactionCount;
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getSourceName() { return sourceName; }
    public String getCategory() { return category; }
    public String getSeverity() { return severity; }
    public String getSummary() { return summary; }
    public String getSanitizedLog() { return sanitizedLog; }
    public String getFindingsJson() { return findingsJson; }
    public String getAiAnalysis() { return aiAnalysis; }
    public boolean isAiUsed() { return aiUsed; }
    public int getRedactionCount() { return redactionCount; }
}

