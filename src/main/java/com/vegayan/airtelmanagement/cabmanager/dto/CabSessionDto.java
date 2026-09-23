package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class CabSessionDto {
    private String id;
    /**
     * The meeting link this session runs on (sp_get_cab_sessions.session_link).
     * Null for a session planned without one - a CAB can be scheduled before the
     * bridge is booked.
     */
    private String sessionLink;
    private String stage;
    private String host;
    private String date;
    private String time;
    private String status;
    private String type;
    private List<String> crqIds;
}
