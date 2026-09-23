package com.vegayan.airtelmanagement.common.exception;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.StageActionErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationException(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describeFieldError)
                .distinct()
                .collect(Collectors.joining("; "));

        if (message.isBlank()) {
            message = "The submitted data is not valid.";
        }

        log.warn("Validation failed at [{} {}] - {}",
                request.getMethod(),
                request.getRequestURI(),
                message);

        ApiResponse response = ApiResponse.builder()
                .status("Error")
                .message(message)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    private static String describeFieldError(FieldError error) {

        String field = error.getField();
        String label = field.isEmpty()
                ? field
                : Character.toUpperCase(field.charAt(0)) + field.substring(1).replaceAll("(?=[A-Z])", " ").trim();

        return label + ": " + error.getDefaultMessage();
    }

    @ExceptionHandler(DatabaseOperationException.class)
    public ResponseEntity<ApiResponse> handleDatabaseException(DatabaseOperationException ex) {

        log.warn("Database error: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse("Error", ex.getMessage()));
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ApiResponse> handlePermissionDeniedException(PermissionDeniedException ex,
                                                                         HttpServletRequest request) {

        log.warn("Permission denied at [{} {}] - Message: {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage());

        ApiResponse response = ApiResponse.builder()
                .status("Error")
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler(ExcelUploadNotFoundException.class)
    public ResponseEntity<ApiResponse> handleExcelUploadNotFoundException(ExcelUploadNotFoundException ex,
                                                                           HttpServletRequest request) {

        log.warn("Excel upload not found at [{} {}] - Message: {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage());

        ApiResponse response = ApiResponse.builder()
                .status("Error")
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }


    @ExceptionHandler(StageActionBlockedException.class)
    public ResponseEntity<StageActionErrorResponse> handleStageActionBlockedException(
            StageActionBlockedException ex,
            HttpServletRequest request) {

        log.warn("Stage action blocked at [{} {}] - crq={} stage={} code={} message={}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getCrqNo(),
                ex.getStage(),
                ex.getCode(),
                ex.getMessage());

        StageActionErrorResponse response = StageActionErrorResponse.builder()
                .status("Error")
                .message(ex.getMessage())
                .code(ex.getCode())
                .hint(ex.getHint())
                .stage(ex.getStage())
                .crqNo(ex.getCrqNo())
                .build();

        return ResponseEntity
                .status(HttpStatus.valueOf(ex.getHttpStatus()))
                .body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse> handleBusinessException(BusinessException ex,
                                                                HttpServletRequest request) {

        log.warn("Business rule violation at [{} {}] - Message: {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage());

        ApiResponse response = ApiResponse.builder()
                .status("Error")
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse> handleDataAccessException(DataAccessException ex,
                                                                 HttpServletRequest request) {

        SQLException signal = findSignalException(ex);

        if (signal != null) {

            log.warn("Procedure rule violation at [{} {}] - Message: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    signal.getMessage());

            ApiResponse response = ApiResponse.builder()
                    .status("Error")
                    .message(signal.getMessage())
                    .build();

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(response);
        }

        log.error("Data access failure at [{} {}] - Message: {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                ex);

        ApiResponse response = ApiResponse.builder()
                .status("Error")
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    private static final String SIGNAL_SQL_STATE = "45000";

    private static SQLException findSignalException(Throwable ex) {

        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {

            if (cause instanceof SQLException sqlEx
                    && SIGNAL_SQL_STATE.equals(sqlEx.getSQLState())
                    && sqlEx.getMessage() != null
                    && !sqlEx.getMessage().isBlank()) {
                return sqlEx;
            }
        }

        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleException(Exception ex,
                                                       HttpServletRequest request) {

        log.error("Unhandled Exception at [{} {}] - Message: {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                ex);

        ApiResponse response = ApiResponse.builder()
                .status("Error")
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

}
