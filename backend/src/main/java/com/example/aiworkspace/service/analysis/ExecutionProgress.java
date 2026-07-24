package com.example.aiworkspace.service.analysis;

/** Callback for reporting analysis stage transitions during async execution. */
@FunctionalInterface
public interface ExecutionProgress {
    void begin(String stepCode);
}
