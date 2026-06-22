package com.replywise.model;

import java.util.Map;

public record ActivityData(
    Map<String, ChartPeriod> chartPeriods,
    HeatmapData heatmap
) {
}
