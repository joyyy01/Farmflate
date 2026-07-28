package com.farmflate.scheduler;

import com.farmflate.domain.region.RegionAnalysisRepository;
import com.farmflate.service.analysis.RegionAnalysisJobDispatcher;
import com.farmflate.service.analysis.RegionAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Re-enqueues only stale work; the worker's conditional claim decides ownership. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionAnalysisRecoveryScheduler {

    private static final int RECOVERY_BATCH_SIZE = 32;

    private final RegionAnalysisRepository analysisRepository;
    private final RegionAnalysisJobDispatcher jobDispatcher;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        requeueStaleAnalyses();
    }

    @Scheduled(fixedDelayString = "${app.region-analysis.recovery-delay-ms:60000}")
    public void recoverOnSchedule() {
        requeueStaleAnalyses();
    }

    private void requeueStaleAnalyses() {
        LocalDateTime staleBefore = LocalDateTime.now().minus(RegionAnalysisService.PROCESSING_STALE_AFTER);
        for (String analysisId : analysisRepository.findRecoveryCandidateIds(staleBefore, PageRequest.of(0, RECOVERY_BATCH_SIZE))) {
            log.info("Requeueing stale region analysis {}", analysisId);
            jobDispatcher.enqueue(analysisId);
        }
    }
}
