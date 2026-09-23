package com.vegayan.airtelmanagement.remedy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.remedy.dto.CrqUpdateChmDto;
import com.vegayan.airtelmanagement.remedy.dto.CrqUpdateChmResponse;
import com.vegayan.airtelmanagement.remedy.dto.RemedyCancelledCrqDetails;
import com.vegayan.airtelmanagement.sygnet.service.CrqStatusUpdateService;
import com.vegayan.airtelmanagement.sygnet.service.PushCrqStatusService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class RemedyService extends BaseService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PushCrqStatusService pushCrqStatusService;

    private final CrqStatusUpdateService crqStatusUpdateService;

    @LogType("CRQ_Update_To_Chm")
    public CrqUpdateChmResponse crqUpdateToChmApi(CrqUpdateChmDto body) {
        // Reuse the correlationId set by LoggingAspect so the transactionId matches the logs
        String transactionId = Optional.ofNullable(MDC.get("correlationId"))
                .orElseGet(() -> UUID.randomUUID().toString());

        String requestJson;
        try {
            requestJson = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create CHM request json", e);
        }

        try {
            crqUpdateToChm.info("Call SP_INSERT_REMEDY_CHANGE_INFO_TBL : {}", requestJson);
//            crqUpdateToChm.info("Call SP_INSERT_REMEDY_CHANGE_INFO_TBL('{}')", requestJson);

            String sql = "CALL SP_INSERT_REMEDY_CHANGE_INFO_TBL(?)";

            ApiResponse apiResponse = databaseUtils.executeProcedureForMessageV1(
                    jdbcTemplateTwo,
                    sql,
                    requestJson
            );

            // --- CRQ STATUS UPDATE START ---
            if (body.getInfrastructureChangeId() != null && !body.getInfrastructureChangeId().trim().isEmpty()) {
                crqUpdateToChm.info(
                        "Triggering CRQ status update to Cygnet for CRQ: {}",
                        body.getInfrastructureChangeId()
                );

                crqStatusUpdateService.crqStatusUpdate(body.getInfrastructureChangeId());
            } else {
                crqUpdateToChm.warn(
                        "CRQ number is missing. Skipping CRQ status update."
                );
            }
            // --- CRQ STATUS UPDATE END ---

            // --- CANCELLATION FLOW START ---
            String status = body.getChangeRequestStatus();


            if (status != null && (status.equalsIgnoreCase("cancel") ||
                    status.equalsIgnoreCase("canceled") ||
                    status.equalsIgnoreCase("cancelled"))) {
                crqUpdateToChm.info("CRQ Status is {}. Triggering Cancellation Flow.", status);

                String cancelSql = "CALL Update_CRQ_To_Cancel_From_Remedy(?)";

                // Fetch safely into the Record/DTO
                RemedyCancelledCrqDetails cancelDetails = databaseUtils.executeProcedureSingleResultWithError(
                        jdbcTemplateTwo,
                        cancelSql,
                        RemedyCancelledCrqDetails.class,
                        body.getInfrastructureChangeId()
                );

                if (cancelDetails != null) {
                    crqUpdateToChm.info("Cancel procedure returned - {}", cancelDetails);

                    // Unpack the record and pass the individual fields to your existing method
                    pushCrqStatusService.pushCrqStatusToCygnet(
                            cancelDetails.crqNo(),
                            cancelDetails.planNumber(),
                            cancelDetails.taskNumber(),
                            cancelDetails.status()
                    );
                } else {
                    crqUpdateToChm.warn("Update_CRQ_To_Cancel_From_Remedy returned no data for Change ID: {}",
                            body.getInfrastructureChangeId());
                }
            }
            // --- CANCELLATION FLOW END ---
            return new CrqUpdateChmResponse(
                    "success",
                    body.getInfrastructureChangeId(),
                    apiResponse.message(),
                    transactionId,
                    false
            );

        } catch (Exception e) {
            crqUpdateToChm.error("DB Operation failed: ", e);

            return new CrqUpdateChmResponse(
                    "fail",
                    body.getInfrastructureChangeId() != null ? body.getInfrastructureChangeId() : "",
                    e.getMessage(),
                    transactionId,
                    true
            );
        }
    }
}
