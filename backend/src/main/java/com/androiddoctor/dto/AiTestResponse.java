package com.androiddoctor.dto;

public record AiTestResponse(boolean success, long latencyMs, String message) {}

