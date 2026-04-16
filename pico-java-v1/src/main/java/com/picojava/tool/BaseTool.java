package com.picojava.tool;

public abstract class BaseTool implements Tool {
    private final String name;
    private final boolean risky;
    private final String schema;
    private final String description;

    protected BaseTool(String name, boolean risky, String schema, String description) {
        this.name = name;
        this.risky = risky;
        this.schema = schema;
        this.description = description;
    }

    @Override public String name() { return name; }
    @Override public boolean risky() { return risky; }
    @Override public String schema() { return schema; }
    @Override public String description() { return description; }
}
