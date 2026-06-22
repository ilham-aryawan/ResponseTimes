package com.replywise.model;

import java.util.List;

public record ChartPeriod(
    List<Integer> values,
    List<Integer> baseline,
    List<String> labels,
    String median
) {
}
