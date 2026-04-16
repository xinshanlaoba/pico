package com.picojava.agent;

public enum ApprovalPolicy {
    ASK,
    AUTO,
    NEVER;

    public static ApprovalPolicy from(String value) {
        return ApprovalPolicy.valueOf(value.trim().toUpperCase());
    }
}
