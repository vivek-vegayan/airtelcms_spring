package com.vegayan.airtelmanagement.impactbatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchStatusResponseDTO {
    private List<String> files;
    private String modifiedDate;
}
