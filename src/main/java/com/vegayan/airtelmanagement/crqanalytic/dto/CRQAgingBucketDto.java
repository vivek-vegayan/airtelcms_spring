package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CRQAgingBucketDto {
    private String bucket;
    private int    ccb;
    private int    se;
}
