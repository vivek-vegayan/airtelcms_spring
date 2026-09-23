package com.vegayan.airtelmanagement.teammanagement.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Maps to sp_get_user_profile() result-set-2 columns (AUTH_LOGIN_AUDIT rows).
 */
@Getter
@Setter
public class UserLoginHistoryModel {
    private Long id;
    private Timestamp loginTime;
    private Timestamp logoutTime;
    private String status;
}
