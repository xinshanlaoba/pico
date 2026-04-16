package com.picojava.tool;

import com.picojava.agent.Pico;

import java.util.Map;

public interface Tool {
    String name();
    boolean risky();
    String schema();
    String description();
    void validate(Pico pico, Map<String, Object> args) throws Exception;
    String execute(Pico pico, Map<String, Object> args) throws Exception;
}
