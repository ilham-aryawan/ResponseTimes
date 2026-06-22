package com.replywise.model;

import java.util.List;

public record HeatmapData(
    List<List<Integer>> values,
    List<String> days,
    List<String> hours
) {
}
