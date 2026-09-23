package com.vegayan.airtelmanagement.attributeupdate.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Combined GET /attributeupdate/details response - one round trip for all 3 systems. */
@Getter
@Setter
@AllArgsConstructor
public class AttributeUpdateDetailsDto {
    /** Latest saved Remedy row for this CRQ + stage, or null if never saved. */
    private RemedyAttrDto remedy;
    /** Latest saved CAB row for this CRQ + stage, or null if never saved. */
    private CabAttrDto cab;
    /**
     * Latest CYGNET_UPDATE_ATTR_TBL row for this CRQ, or null if none exists.
     * Serializes under that table's own column names rather than in camelCase
     * - see CygnetAttrDto for why.
     */
    private CygnetAttrDto cygnet;
    /**
     * The CRQ's live Remedy status straight from GET_CHANGE_REQUEST_STATUS,
     * e.g. "Scheduled For Review" - null if the procedure returned no row.
     *
     * This is where the CRQ actually stands, as opposed to the status the last
     * save happened to write, so it is what the dialog's Remedy sub-status bar
     * opens on and refuses to move back before. Carried here rather than on its
     * own endpoint because every card that renders that bar is already waiting
     * on this response.
     */
    private String remedyStatus;
}
