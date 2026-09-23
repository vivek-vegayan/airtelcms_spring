package com.vegayan.airtelmanagement.teammanagement.dto;

import com.vegayan.airtelmanagement.teammanagement.model.UserLoginHistoryModel;
import com.vegayan.airtelmanagement.teammanagement.model.UserPermissionModel;
import com.vegayan.airtelmanagement.teammanagement.model.UserProfileModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class UserProfileResponseDto {
    private UserProfileModel profile;
    private List<UserLoginHistoryModel> loginHistory;
    private List<UserPermissionModel> permissions;
}
