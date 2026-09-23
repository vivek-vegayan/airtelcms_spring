package com.vegayan.airtelmanagement.teammanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class ExcelUploadAsyncConfig {

    @Bean(name = "excelUploadExecutor")
    public Executor excelUploadExecutor(ExcelUploadProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getExecutorCorePoolSize());
        executor.setMaxPoolSize(props.getExecutorMaxPoolSize());
        executor.setQueueCapacity(props.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("excel-upload-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
