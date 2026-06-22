package com.replywise.model;

public record SummaryStats(
    String medianResponseTime,
    String medianTrend,
    String conversationCount,
    String conversationDetail,
    String fastestName,
    String fastestDetail,
    String bestTime,
    String bestTimeDetail
) {
}
