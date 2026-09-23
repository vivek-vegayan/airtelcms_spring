package com.vegayan.airtelmanagement.schedular.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CrqDetailsResponseDto {

    private CrqDetailsInfoDto info;
    private List<CrqDetailsStageDto> stages;
}
