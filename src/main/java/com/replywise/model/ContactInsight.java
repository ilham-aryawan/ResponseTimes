package com.replywise.model;

public record ContactInsight(
    String id,
    String name,
    String initials,
    String handle,
    double time,
    String unit,
    String avatar,
    String responseRate,
    String bestTime,
    int conversations,
    String streak,
    String insight
) {
}
