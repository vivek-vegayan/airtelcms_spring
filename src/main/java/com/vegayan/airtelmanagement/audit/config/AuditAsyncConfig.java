package com.vegayan.airtelmanagement.audit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The pool audit rows are written on.
 *
 * <p>Its own executor rather than the shared one: an audit write is tiny and
 * frequent, and must not queue behind a long-running Excel import. Deliberately
 * NOT annotated {@code @EnableAsync} - {@code ExcelUploadAsyncConfig} already
 * enables async proxying application-wide, and a second activation of the same
 * infrastructure is at best redundant.
 *
 * <p>{@code ThreadPoolExecutor.CallerRunsPolicy} is the important choice here.
 * If the queue ever fills, the audit write happens inline on the request thread
 * instead of being discarded: a slower request is an acceptable cost, a missing
 * audit record is not. The queue is sized so that this is a pressure valve, not
 * the normal path.
 */
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
