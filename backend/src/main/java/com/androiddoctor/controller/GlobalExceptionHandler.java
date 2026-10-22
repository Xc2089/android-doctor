package com.androiddoctor.controller;

import com.androiddoctor.service.DiagnosisService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof MaxUploadSizeExceededException ? "上传文件不能超过 2 MB" : exception.getMessage();
        return error(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception,
                                                          HttpServletRequest request) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        return error(HttpStatus.BAD_REQUEST, fieldError == null ? "请求参数无效" : fieldError.getDefaultMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(DiagnosisService.ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时不可用，请稍后重试", request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("message", message);
        body.put("path", path);
        return ResponseEntity.status(status).body(body);
    }
}
