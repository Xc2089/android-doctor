package com.androiddoctor.controller;

import com.androiddoctor.config.AppProperties;
import com.androiddoctor.dto.AiConfigUpdateRequest;
import com.androiddoctor.dto.AiConfigView;
import com.androiddoctor.dto.AiTestResponse;
import com.androiddoctor.dto.AnalysisRequest;
import com.androiddoctor.dto.DiagnosisResponse;
import com.androiddoctor.dto.HistoryItem;
import com.androiddoctor.service.DiagnosisService;
import com.androiddoctor.service.AiAnalysisService;
import com.androiddoctor.service.AiRuntimeConfigService;
import com.androiddoctor.service.MarkdownReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DiagnosisController {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "log", "trace", "tombstone");
    private final DiagnosisService diagnosisService;
    private final AppProperties properties;
    private final AiRuntimeConfigService aiConfigService;
    private final AiAnalysisService aiAnalysisService;
    private final MarkdownReportService markdownReportService;

    public DiagnosisController(DiagnosisService diagnosisService, AppProperties properties,
                               AiRuntimeConfigService aiConfigService, AiAnalysisService aiAnalysisService,
                               MarkdownReportService markdownReportService) {
        this.diagnosisService = diagnosisService;
        this.properties = properties;
        this.aiConfigService = aiConfigService;
        this.aiAnalysisService = aiAnalysisService;
        this.markdownReportService = markdownReportService;
    }

    @PostMapping(value = "/diagnoses", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DiagnosisResponse analyze(@Valid @RequestBody AnalysisRequest request) {
        return diagnosisService.analyze(request.log(), request.sourceName(), Boolean.TRUE.equals(request.useAi()));
    }

    @PostMapping(value = "/diagnoses/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DiagnosisResponse upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(defaultValue = "false") boolean useAi) throws Exception {
        if (file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        if (file.getSize() > 2L * 1024 * 1024) throw new IllegalArgumentException("上传文件不能超过 2 MB");
        String filename = file.getOriginalFilename() == null ? "uploaded.log" : file.getOriginalFilename();
        String safeName = filename.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        String extension = safeName.contains(".") ? safeName.substring(safeName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 .txt、.log、.trace、.tombstone 文本文件");
        }
        String log = new String(file.getBytes(), StandardCharsets.UTF_8);
        return diagnosisService.analyze(log, safeName, useAi);
    }

    @GetMapping("/diagnoses")
    public List<HistoryItem> history(@RequestParam(defaultValue = "50") int limit) {
        return diagnosisService.history(limit);
    }

    @GetMapping("/diagnoses/{id}")
    public DiagnosisResponse get(@PathVariable UUID id) {
        return diagnosisService.get(id);
    }

    @GetMapping(value = "/diagnoses/{id}/report.md", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<byte[]> report(@PathVariable UUID id) {
        String markdown = markdownReportService.render(diagnosisService.get(id));
        String filename = "android-doctor-" + id.toString().substring(0, 8) + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(markdown.getBytes(StandardCharsets.UTF_8));
    }

    @DeleteMapping("/diagnoses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        diagnosisService.delete(id);
    }

    @DeleteMapping("/diagnoses")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() {
        diagnosisService.clear();
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "ai", aiConfigService.view(),
                "maxLogChars", properties.maxLogChars(),
                "maxUploadBytes", 2 * 1024 * 1024
        );
    }

    @org.springframework.web.bind.annotation.PutMapping("/config/ai")
    public AiConfigView updateAiConfig(@Valid @RequestBody AiConfigUpdateRequest request) {
        return aiConfigService.update(request);
    }

    @PostMapping("/config/ai/reset")
    public AiConfigView resetAiConfig() {
        return aiConfigService.resetToEnvironment();
    }

    @PostMapping("/config/ai/test")
    public AiTestResponse testAiConfig() {
        return aiAnalysisService.testConnection();
    }
}

