package com.vegayan.airtelmanagement.remedy.dto;

import lombok.Data;

@Data
public class RemedyErrorFullDto {
    private String messageType;
    private String messageText;
    private String messageAppendedText;
    private Integer messageNumber;
}
