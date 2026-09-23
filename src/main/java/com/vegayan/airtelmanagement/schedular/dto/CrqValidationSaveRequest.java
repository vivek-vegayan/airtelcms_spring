package com.vegayan.airtelmanagement.schedular.dto;

/**
 * Body of POST /crqworkflow/validation/save - the three arguments of
 * update_validation_details(p_Crq_No, p_NodeName, p_NameInterfacePair).
 *
 * Only Node Name and Name Interface Pair are editable; crqNo identifies the
 * row and is read-only in the dialog.
 */
public record CrqValidationSaveRequest(
        String crqNo,
        String nodeName,
        String nameInterfacePair
) {
}
