package com.replywise.model;

import java.time.Instant;

public record RawMessage(
    String contact,
    Instant timestamp,
    boolean fromMe
) {
}
