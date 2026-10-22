package com.androiddoctor.service;

import com.androiddoctor.config.AppProperties;
import com.androiddoctor.dto.DiagnosisResponse;
import com.androiddoctor.dto.FindingDto;
import com.androiddoctor.dto.HistoryItem;
import com.androiddoctor.model.Diagnosis;
import com.androiddoctor.repository.DiagnosisRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DiagnosisService {
    private final DiagnosisRepository repository;
    private final LogSanitizer sanitizer;
    private final RuleEngineService ruleEngine;
    private final AiAnalysisService aiService;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public DiagnosisService(DiagnosisRepository repository, LogSanitizer sanitizer,
                            RuleEngineService ruleEngine, AiAnalysisService aiService,
                            ObjectMapper objectMapper, AppProperties properties) {
        this.repository = repository;
        this.sanitizer = sanitizer;
        this.ruleEngine = ruleEngine;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public DiagnosisResponse analyze(String log, String sourceName, boolean useAi) {
        if (log == null || log.isBlank()) throw new IllegalArgumentException("日志内容不能为空");
        if (log.length() > properties.maxLogChars()) {
            throw new IllegalArgumentException("日志不能超过 " + properties.maxLogChars() + " 个字符");
        }
        LogSanitizer.SanitizedLog sanitized = sanitizer.sanitize(log);
        List<FindingDto> findings = ruleEngine.analyze(sanitized.value());
        FindingDto primary = findings.getFirst();
        AiAnalysisService.AiResult aiResult = aiService.analyze(sanitized.value(), findings, useAi);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String safeSourceName = normalizeSourceName(sourceName);
        String summary = primary.title() + (findings.size() > 1 ? "，另发现 " + (findings.size() - 1) + " 项" : "");
        Diagnosis entity = new Diagnosis(id, now, safeSourceName, primary.category(), primary.severity(),
                summary, sanitized.value(), writeFindings(findings), aiResult.content(),
                aiResult.content() != null, sanitized.redactionCount());
        repository.save(entity);
        return toResponse(entity, findings, aiResult.warning());
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> history(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, properties.historyLimit()));
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(item -> new HistoryItem(item.getId(), item.getCreatedAt(), item.getSourceName(),
                        item.getCategory(), item.getSeverity(), item.getSummary(), item.isAiUsed(),
                        item.getRedactionCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DiagnosisResponse get(UUID id) {
        Diagnosis entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("诊断记录不存在"));
        return toResponse(entity, readFindings(entity.getFindingsJson()), null);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("诊断记录不存在");
        repository.deleteById(id);
    }

    @Transactional
    public void clear() {
        repository.deleteAllInBatch();
    }

    private DiagnosisResponse toResponse(Diagnosis entity, List<FindingDto> findings, String warning) {
        return new DiagnosisResponse(entity.getId(), entity.getCreatedAt(), entity.getSourceName(),
                entity.getCategory(), entity.getSeverity(), entity.getSummary(), entity.getSanitizedLog(),
                entity.getRedactionCount(), findings, entity.isAiUsed(), entity.getAiAnalysis(), warning);
    }

    private String writeFindings(List<FindingDto> findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存诊断结果", exception);
        }
    }

    private List<FindingDto> readFindings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取诊断结果", exception);
        }
    }

    private String normalizeSourceName(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) return "粘贴日志";
        return sourceName.strip().substring(0, Math.min(sourceName.strip().length(), 255));
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) { super(message); }
    }
}

