package com.androiddoctor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ai_configuration")
public class AiConfiguration {
    @Id
    private Long id;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false, length = 500)
    private String baseUrl;
    @Column(nullable = false, length = 200)
    private String model;
    @Column(nullable = false)
    private int timeoutSeconds;
    @Lob
    private String encryptedApiKey;
    @Column(nullable = false)
    private Instant updatedAt;

    protected AiConfiguration() {}

    public AiConfiguration(Long id, boolean enabled, String baseUrl, String model, int timeoutSeconds,
                           String encryptedApiKey, Instant updatedAt) {
        this.id = id;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.encryptedApiKey = encryptedApiKey;
        this.updatedAt = updatedAt;
    }

    public boolean isEnabled() { return enabled; }
    public String getBaseUrl() { return baseUrl; }
    public String getModel() { return model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public String getEncryptedApiKey() { return encryptedApiKey; }
    public Instant getUpdatedAt() { return updatedAt; }
}

