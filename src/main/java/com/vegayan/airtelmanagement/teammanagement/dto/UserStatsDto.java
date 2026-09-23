package com.vegayan.airtelmanagement.teammanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Unfiltered aggregate counts across all users, appended to
 * sp_get_users_paginated() result-set-1 alongside the filtered total_count
 * so the dashboard's stat cards and grid are served in a single call.
 */
@Getter
@Setter
@AllArgsConstructor
public class UserStatsDto {
    private long activeCount;
    private long inactiveCount;
    private long adminCount;
    private long headCount;
    private long newThisMonth;
}
