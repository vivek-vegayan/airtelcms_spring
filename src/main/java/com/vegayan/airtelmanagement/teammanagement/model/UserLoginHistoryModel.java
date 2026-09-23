package com.vegayan.airtelmanagement.teammanagement.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class UserLoginHistoryModel {
    private Long id;
    private Timestamp loginTime;
    private Timestamp logoutTime;
    private String status;
}
