package com.replywise.model;

import java.util.List;

public record DashboardData(
    SummaryStats summary,
    List<ContactInsight> contacts,
    ActivityData activity
) {
}
