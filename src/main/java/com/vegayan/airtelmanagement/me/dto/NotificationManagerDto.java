package com.vegayan.airtelmanagement.me.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationManagerDto {
    private Integer configId;
    private String moduleCode;
    private String subModuleCode;
    private String actionCode;

    private Boolean notifySuperAdmin;
    private Boolean notifyVerticalHead;
    private Boolean notifyFunctionHead;
    private Boolean notifyDomainHead;
    private Boolean notifySubDomainHead;
    private Boolean notifyTeamMember;

    private Boolean isActive;
}
