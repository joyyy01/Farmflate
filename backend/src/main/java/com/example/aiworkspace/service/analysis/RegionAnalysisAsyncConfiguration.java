package com.example.aiworkspace.service.analysis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Bounded worker pool so provider calls never occupy the HTTP request thread. */
@Configuration
public class RegionAnalysisAsyncConfiguration {

    @Bean(name = "regionAnalysisExecutor")
    public TaskExecutor regionAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("region-analysis-");
        executor.initialize();
        return executor;
    }
}
