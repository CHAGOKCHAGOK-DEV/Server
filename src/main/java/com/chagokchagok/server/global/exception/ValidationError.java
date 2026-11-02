package com.chagokchagok.server.global.exception;

public record ValidationError(
    String field,
    String message
) {
}
