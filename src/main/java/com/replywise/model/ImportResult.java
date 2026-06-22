package com.replywise.model;

public record ImportResult(
    String source,
    int contactsAnalyzed,
    String message
) {
}
