package com.shufflepix.common.dto;

public record ErrorResponse(
        int status,
        String code,
        String message) {
}
