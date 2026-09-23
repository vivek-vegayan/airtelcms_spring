package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

/**
 * Raw row from {@code sp_add_crq_to_cab_session} - the two CRQ lists arrive as
 * JSON array literals, which {@link AddCrqToSessionResultDto} carries as real
 * lists once parsed.
 */
@Data
public class AddCrqToSessionRowDto {
    private String cabId;
    private Integer addedCount;
    private Integer skippedCount;
    private String addedCrqList;
    private String skippedCrqList;
}
