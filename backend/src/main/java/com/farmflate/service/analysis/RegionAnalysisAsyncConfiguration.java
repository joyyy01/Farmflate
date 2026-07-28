package com.farmflate.service.analysis;

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

    @Bean(name = "regionProviderExecutor")
    public TaskExecutor regionProviderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("region-provider-");
        executor.initialize();
        return executor;
    }
}
