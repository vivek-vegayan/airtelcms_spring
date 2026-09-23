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

    /**
     * Bean-validation failures on an {@code @Valid @RequestBody}.
     *
     * Without this, {@code MethodArgumentNotValidException} fell through to the
     * catch-all {@code Exception} handler at the bottom, which answered 500 with
     * {@code ex.getMessage()} - the full framework string ("Validation failed for
     * argument [0] in public ... with 2 errors: [Field error in object '...' on
     * field 'gender': rejected value [null]; codes [...]") - and the UI dropped
     * that whole thing into a toast. Answer 400 with just the field messages.
     */
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

    /** "Email: Invalid email format" - readable enough to show verbatim in a toast. */
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

    /**
     * A stage outcome the stored procedure refused (CAB pending, Ops task
     * still open, CRQ already moved on, ...). Nothing was written, so this
     * must never look like a success. Declared separately from
     * {@link BusinessException} - which it extends - so the machine
     * {@code code}/{@code hint} reach the UI and the status can be 404 when
     * the CRQ itself is gone.
     */
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

    /**
     * A procedure that raises `SIGNAL SQLSTATE '45000'` is stating a business
     * rule ("this reason already exists"), not crashing. Spring wraps that in an
     * UncategorizedSQLException whose message buries the rule under JDBC
     * plumbing - "CallableStatementCallback; uncategorized SQLException; SQL
     * state [45000]; error code [1644]; ..." - and the catch-all below used to
     * pass that whole string to the UI, where it landed in a toast verbatim.
     *
     * Unwrap to the SIGNAL text and answer 409 so callers can show it as-is.
     * Every other data-access failure keeps the previous behaviour.
     */
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

    /** The SQLSTATE MySQL reserves for a user-raised SIGNAL. */
    private static final String SIGNAL_SQL_STATE = "45000";

    /** Walks the cause chain for a proc's deliberate SIGNAL, or null if none. */
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
