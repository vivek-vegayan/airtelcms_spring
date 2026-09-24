package com.vegayan.airtelmanagement.sygnet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Response of Cygnet fetchPlanEquipmentAndLinkDetails.
 * Only the fields we use are mapped; anything else is ignored.
 * equipmentData / linkSummary can come as null - always null-check.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanDetailsResponse {

    private String status;
    private String message;
    private String errorCode;
    private Data data;

    public boolean isSuccess() {
        return status != null && "SUCCESS".equalsIgnoreCase(status.trim());
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String planNumber;
        private List<Equipment> equipmentData;
        private List<Link> linkSummary;
    }

    /** equipmentData[] - has a node, but no interface name. */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Equipment {
        private String neLabel;
    }

    /** linkSummary[] - each link has an A end and a Z end (node + interface). */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Link {
        // Explicit names: Lombok's getAEndNeLabel() would otherwise map to "aendNeLabel"
        @JsonProperty("aEndNeLabel")
        private String aEndNeLabel;

        @JsonProperty("aEndPtpMoName")
        private String aEndPtpMoName;

        @JsonProperty("zEndNeLabel")
        private String zEndNeLabel;

        @JsonProperty("zEndPtpMoName")
        private String zEndPtpMoName;
    }
}
