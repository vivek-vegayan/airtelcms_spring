package com.vegayan.airtelmanagement.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcedurePageResult<T> {

    private long totalCount;
    private List<T> data;
}
