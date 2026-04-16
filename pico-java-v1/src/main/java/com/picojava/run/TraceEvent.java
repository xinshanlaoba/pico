package com.picojava.run;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TraceEvent(
        String runId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        String eventType,
        Integer step,
        Map<String, Object> data
) {
    public TraceEvent {
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public static TraceEvent of(String runId, String eventType, Integer step, Map<String, Object> data) {
        return new TraceEvent(runId, Instant.now(), eventType, step, data);
    }
}
