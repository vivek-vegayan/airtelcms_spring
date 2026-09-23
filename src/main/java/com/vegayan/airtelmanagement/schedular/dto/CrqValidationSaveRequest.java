package com.vegayan.airtelmanagement.schedular.dto;


public record CrqValidationSaveRequest(
        String crqNo,
        String nodeName,
        String nameInterfacePair
) {
}
