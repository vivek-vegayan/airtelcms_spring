package com.vegayan.airtelmanagement.audit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AuditAsyncConfig {

    public static final String EXECUTOR = "auditLogExecutor";

    @Bean(name = EXECUTOR)
    public Executor auditLogExecutor(
            @Value("${audit.log.executor-core-pool-size:2}") int corePoolSize,
            @Value("${audit.log.executor-max-pool-size:4}") int maxPoolSize,
            @Value("${audit.log.executor-queue-capacity:500}") int queueCapacity) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("audit-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
