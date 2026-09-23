package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.teammanagement.config.ExcelUploadProperties;
import com.vegayan.airtelmanagement.teammanagement.dto.EmployeeExcelRowDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelRowResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeExcelBatchProcessorTest {

    @Mock
    private EmployeeExcelService employeeExcelService;

    private List<EmployeeExcelRowDto> rows(int count) {
        List<EmployeeExcelRowDto> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            EmployeeExcelRowDto row = new EmployeeExcelRowDto();
            row.setOlmid("OLM" + i);
            list.add(row);
        }
        return list;
    }

    @Test
    void chunksRowsAccordingToConfiguredBatchSize() {
        ExcelUploadProperties props = new ExcelUploadProperties();
        props.setBatchSize(10);
        EmployeeExcelBatchProcessor processor = new EmployeeExcelBatchProcessor(employeeExcelService, props);

        when(employeeExcelService.createEmployeesBatch(anyLong(), anyList(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(inv -> {
                    List<EmployeeExcelRowDto> chunk = inv.getArgument(1);
                    int startRow = inv.getArgument(2);
                    List<ExcelRowResultDto> results = new ArrayList<>();
                    for (int i = 0; i < chunk.size(); i++) {
                        results.add(new ExcelRowResultDto(startRow + i, chunk.get(i).getOlmid(), "SUCCESS", "ok"));
                    }
                    return results;
                });

        ExcelBatchProcessingResult result =
                processor.processInBatches(1L, "TEST-ID", rows(25), BatchProgressListener.NO_OP);

        assertEquals(3, result.totalBatches()); // 10 + 10 + 5
        assertEquals(25, result.results().size());
        verify(employeeExcelService, times(3))
                .createEmployeesBatch(anyLong(), anyList(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void continueOnBatchFailureFalseStopsAndMarksRemainderSkipped() {
        ExcelUploadProperties props = new ExcelUploadProperties();
        props.setBatchSize(5);
        props.setContinueOnBatchFailure(false);
        EmployeeExcelBatchProcessor processor = new EmployeeExcelBatchProcessor(employeeExcelService, props);

        when(employeeExcelService.createEmployeesBatch(anyLong(), anyList(), eq(1)))
                .thenThrow(new RuntimeException("simulated DB outage"));

        ExcelBatchProcessingResult result =
                processor.processInBatches(1L, "TEST-ID", rows(15), BatchProgressListener.NO_OP);

        assertEquals(3, result.totalBatches());
        assertEquals(1, result.batchesFailedEntirely());
        assertEquals(15, result.results().size());

        long failedCount = result.results().stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        long skippedCount = result.results().stream().filter(r -> "SKIPPED".equals(r.getStatus())).count();
        assertEquals(5, failedCount);
        assertEquals(10, skippedCount);

        verify(employeeExcelService, times(1))
                .createEmployeesBatch(anyLong(), anyList(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void continueOnBatchFailureTrueProceedsToRemainingBatches() {
        ExcelUploadProperties props = new ExcelUploadProperties();
        props.setBatchSize(5);
        props.setContinueOnBatchFailure(true);
        EmployeeExcelBatchProcessor processor = new EmployeeExcelBatchProcessor(employeeExcelService, props);

        when(employeeExcelService.createEmployeesBatch(anyLong(), anyList(), eq(1)))
                .thenThrow(new RuntimeException("simulated DB outage"));
        when(employeeExcelService.createEmployeesBatch(anyLong(), anyList(), eq(6)))
                .thenAnswer(inv -> {
                    List<EmployeeExcelRowDto> chunk = inv.getArgument(1);
                    List<ExcelRowResultDto> results = new ArrayList<>();
                    for (int i = 0; i < chunk.size(); i++) {
                        results.add(new ExcelRowResultDto(6 + i, chunk.get(i).getOlmid(), "SUCCESS", "ok"));
                    }
                    return results;
                });

        ExcelBatchProcessingResult result =
                processor.processInBatches(1L, "TEST-ID", rows(10), BatchProgressListener.NO_OP);

        assertEquals(2, result.totalBatches());
        assertEquals(1, result.batchesFailedEntirely());

        long failedCount = result.results().stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        long successCount = result.results().stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
        assertEquals(5, failedCount);
        assertEquals(5, successCount);

        verify(employeeExcelService, times(2))
                .createEmployeesBatch(anyLong(), anyList(), org.mockito.ArgumentMatchers.anyInt());
    }
}
