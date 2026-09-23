package com.vegayan.airtelmanagement.teammanagement.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * The ONLY place {@code @Async} appears in this pipeline. Accepts a plain
 * {@link Runnable} rather than depending on {@code ExcelUploadService}
 * directly - that keeps this class dependency-free (avoiding a circular
 * bean reference, since {@code ExcelUploadService} is the one that calls
 * into this runner) while still giving a genuine cross-bean method call for
 * Spring's async proxy to intercept.
 */
@Component
public class ExcelUploadAsyncRunner {

    @Async("excelUploadExecutor")
    public void runInBackground(Runnable pipeline) {
        pipeline.run();
    }
}
