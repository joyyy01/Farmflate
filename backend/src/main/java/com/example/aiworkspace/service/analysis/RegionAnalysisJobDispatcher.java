package com.example.aiworkspace.service.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Starts a committed analysis asynchronously; it never fabricates progress. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionAnalysisJobDispatcher {

    @Qualifier("regionAnalysisExecutor")
    private final TaskExecutor regionAnalysisExecutor;
    private final RegionAnalysisService regionAnalysisService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(RegionAnalysisJobRequestedEvent event) {
        try {
            regionAnalysisExecutor.execute(() -> regionAnalysisService.executePersistedAnalysis(event.analysisId()));
        } catch (TaskRejectedException exception) {
            log.warn("Region analysis job queue rejected {}", event.analysisId());
            regionAnalysisService.markDispatchRejected(event.analysisId());
        }
    }
}
