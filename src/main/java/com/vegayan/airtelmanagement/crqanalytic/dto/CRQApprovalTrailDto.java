package com.vegayan.airtelmanagement.crqanalytic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CRQApprovalTrailDto {

    @JsonProperty("role")   private String role;    // role
    @JsonProperty("name")   private String name;    // name
    @JsonProperty("date")   private String date;    // action_date formatted
    @JsonProperty("remark") private String remark;  // remark
    @JsonProperty("status") private String status;  // "approved"|"pending"|"rejected"|"delegated"
}
