package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelCrqValuesDto {
    @JsonProperty("z1DAction")
    private String z1DAction = "UPDATE_STATUS";

    @JsonProperty("Infrastructure Change ID")
    private String infrastructureChangeId;

    @JsonProperty("Description")
    private String Description = "Updated";

    @JsonProperty("Detailed Description")
    private String DetailedDescription = "Updated";

    @JsonProperty("Field1")
    private String Field1;

    @JsonProperty("Field2")
    private String Field2 = "No Longer Required";

    @JsonProperty("Field3")
    private String Field3 ;

    @JsonProperty("Field4")
    private String Field4 ;

    @JsonProperty("Field5")
    private String Field5 ;

    @JsonProperty("Source")
    private String Source = "CHM";
}
