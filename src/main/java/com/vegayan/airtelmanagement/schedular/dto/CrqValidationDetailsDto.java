package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Row returned by get_crq_validation_details (see
 * db/migration/2026-07-28_crq_validation_details.sql), consumed by the
 * Plan &amp; Inventory "Validate" dialog.
 *
 * A plain bean rather than a record because DatabaseUtils maps procedure
 * result sets with BeanPropertyRowMapper, which needs a no-arg constructor
 * and setters. Column labels (Crq_No, Plan_Id, NodeName, NameInterfacePair,
 * ...) map onto these properties by BeanPropertyRowMapper's usual
 * case/underscore-insensitive matching.
 *
 * currentStage / validationStatus come straight off CRQ_MASTER_TBL and are
 * read-only header context - the dialog never writes them, so the workflow
 * is untouched.
 */
@Getter
@Setter
public class CrqValidationDetailsDto {
    private String crqNo;
    private Long planId;
    private String nodeName;
    private String nameInterfacePair;
    private String currentStage;
    private String validationStatus;
    private LocalDateTime updatedAt;
}
