package com.androiddoctor.dto;

public record AdminStatusResponse(
        boolean authenticated,
        String passwordSource,
        String passwordHint
) {}

