package com.vegayan.airtelmanagement.schedular.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CrqOverviewDto extends BaseCrqDto {
    private LocalDateTime enteredCurrentStageAt;

    @JsonProperty("crqRaisedDate")
    private LocalDateTime raised;
}
