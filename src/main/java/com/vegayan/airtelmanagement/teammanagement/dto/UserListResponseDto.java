package com.vegayan.airtelmanagement.teammanagement.dto;

import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.teammanagement.model.UserListModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UserListResponseDto {
    private PageResponseDto<UserListModel> page;
    private UserStatsDto stats;
}
