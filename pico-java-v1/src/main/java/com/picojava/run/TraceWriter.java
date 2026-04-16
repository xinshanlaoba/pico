package com.picojava.run;

import com.picojava.common.JsonUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class TraceWriter {
    private final Path path;

    public TraceWriter(Path path) {
        this.path = path;
    }

    public synchronized void append(TraceEvent event) throws IOException {
        Files.createDirectories(path.getParent());
        try (var writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
        )) {
            writer.write(JsonUtils.MAPPER.writeValueAsString(event));
            writer.newLine();
        }
    }
}
