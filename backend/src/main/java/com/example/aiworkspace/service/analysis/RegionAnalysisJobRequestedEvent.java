package com.example.aiworkspace.service.analysis;

/** Published only after the PENDING analysis row has been committed. */
public record RegionAnalysisJobRequestedEvent(String analysisId) {
}
