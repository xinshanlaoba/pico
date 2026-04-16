package com.picojava.run;

import java.io.IOException;
import java.nio.file.Path;

public class ReportWriter {
    private final Path path;

    public ReportWriter(Path path) {
        this.path = path;
    }

    public void write(Object report) throws IOException {
        RunStore.writeJsonAtomic(path, report);
    }
}
