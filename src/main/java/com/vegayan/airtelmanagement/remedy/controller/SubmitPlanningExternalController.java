package com.vegayan.airtelmanagement.remedy.controller;

import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.service.CommonService;
import com.vegayan.airtelmanagement.remedy.dto.AdditionalCrqInfo;
import com.vegayan.airtelmanagement.remedy.dto.CrqCheckDto;
import com.vegayan.airtelmanagement.remedy.dto.CrqCheckResponseDto;
import com.vegayan.airtelmanagement.remedy.service.SubmitPlanningExternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequestMapping("/planning")
@RequiredArgsConstructor
public class SubmitPlanningExternalController extends BaseService {

    private final SubmitPlanningExternalService submitPlanningExternalService;
    private final CommonService commonService;

    @LogType("Submit_Plan_External_Logger")
    @PostMapping("/v1/submit")
    public ResponseEntity<Map<String, Object>> submitPlanningDetails(
            @RequestBody AdditionalCrqInfo info
    ) {

        submitPlanExternalLogger.info("[SubmitPlan] Incoming Submit_Plan Request JSON:\\n{}",
                commonService.prettyPrintJson(info));

        Map<String, Object> response =
                submitPlanningExternalService.handleSubmitPlanningDetailsExternal(info);

        // Decide HTTP status based on submission_status
        String status = (String) response.get("submission_status");
        String message = (String) response.get("message");

        // 🟢 Success → 200
        if ("Success".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(response);
        }

        // 🔴 Business Error (Duplicate CRQ / Validation issue) → 400
        if (message != null && message.contains("Already available")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // 🔴 System Error → 500
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    @LogType("Submit_Plan_External_Logger")
    @PostMapping("/v1/slot_crq_check")
    public ResponseEntity<CrqCheckResponseDto> crqCheck(
            @RequestBody CrqCheckDto req
    ) {
        try {
            CrqCheckResponseDto response = submitPlanningExternalService.crqCheck(req);
            return ResponseEntity.ok(response);

        } catch (DatabaseOperationException ex) {
            CrqCheckResponseDto errorResponse = new CrqCheckResponseDto(
                    "SUCCESS",
                    req.checkfor(),
                    ex.getMessage()
            );
            return ResponseEntity.ok(errorResponse);
        }
    }
}
