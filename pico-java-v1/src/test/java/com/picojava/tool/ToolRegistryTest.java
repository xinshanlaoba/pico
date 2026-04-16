package com.picojava.tool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ToolRegistryTest {
    @Test
    void registersDefaultToolSetIncludingDelegate() {
        ToolRegistry registry = new ToolRegistry();

        List<String> names = registry.all().stream().map(Tool::name).toList();

        Assertions.assertEquals(
                List.of("list_files", "read_file", "search", "run_shell", "write_file", "patch_file", "delegate"),
                names
        );
        Assertions.assertTrue(registry.findOptional("delegate").isPresent());
        Assertions.assertTrue(registry.find("delegate").schema().contains("max_steps"));
        Assertions.assertFalse(registry.find("delegate").risky());
    }

    @Test
    void returnsNullForUnknownTool() {
        ToolRegistry registry = new ToolRegistry();
        Assertions.assertNull(registry.find("missing"));
        Assertions.assertTrue(registry.findOptional("missing").isEmpty());
    }
}
