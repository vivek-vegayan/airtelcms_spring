package com.vegayan.airtelmanagement.cabmanager.dto;

import java.util.List;

/**
 * Answer to "is a CAB session already booked for this date + time?", behind
 * GET /cab/sessions/conflict.
 *
 * When one exists, cabId / sessionLink / crqList describe it so the planner can
 * see what it already holds and add to it on the same link instead of creating a
 * second session in the slot. No session in the slot means conflict=false, a
 * null cabId and an empty crqList.
 */
public record CabPlanConflictDto(
        boolean conflict,
        String cabId,
        String sessionLink,
        List<String> crqList
) {
}
