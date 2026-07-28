package com.farmflate.service.analysis;

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
        enqueue(event.analysisId());
    }

    public void enqueue(String analysisId) {
        try {
            regionAnalysisExecutor.execute(() -> regionAnalysisService.executePersistedAnalysis(analysisId));
        } catch (TaskRejectedException exception) {
            log.warn("Region analysis job queue rejected {}", analysisId);
            regionAnalysisService.markDispatchRejected(analysisId);
        }
    }
}
