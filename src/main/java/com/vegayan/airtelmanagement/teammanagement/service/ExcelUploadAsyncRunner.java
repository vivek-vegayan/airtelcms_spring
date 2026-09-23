package com.vegayan.airtelmanagement.teammanagement.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ExcelUploadAsyncRunner {

    @Async("excelUploadExecutor")
    public void runInBackground(Runnable pipeline) {
        pipeline.run();
    }
}
