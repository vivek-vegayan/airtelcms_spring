package com.vegayan.airtelmanagement.teammanagement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "excel.upload")
public class ExcelUploadProperties {

    private int batchSize = 100;
    private boolean continueOnBatchFailure = true;
    private int jobRetentionHours = 24;

    private int executorCorePoolSize = 2;
    private int executorMaxPoolSize = 4;
    private int executorQueueCapacity = 50;
}
