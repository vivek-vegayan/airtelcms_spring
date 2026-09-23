package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One row of the SPOC result set of sp_get_crq_journey_page (added to the
 * procedure on 2026-09-08) - the single point of contact recorded against one
 * CAB service of this CRQ.
 * <p>
 * A faithful pass-through of the three columns the procedure emits
 * (Service_Code, Spoc_Name, Spoc_Contact). As with the pending-approval rows,
 * the procedure is read-only for this feature, so everything the UI needs
 * beyond these raw columns is derived downstream (see summarizeServiceSpocs in
 * src/features/crqJourney/utils/crqJourney.utils.ts). Four things about the raw
 * shape matter to a consumer:
 * <ul>
 *   <li>It covers EVERY service linked to the CRQ, not only the pending ones -
 *       unlike {@link CrqPendingApprovalDto}, which is filtered to
 *       Status = 'PENDING'.</li>
 *   <li>{@code serviceCode} is the raw CRQ_CAB_SERVICE_MASTER code. The
 *       procedure resolves the display name internally but does not select it,
 *       so the name has to come from the journey rows or the mirrored code map.</li>
 *   <li>{@code serviceCode} doubles as a sentinel channel: the literal
 *       'NO SERVICES' arrives as the only row, both SPOC fields null, when the
 *       CRQ has no CAB service at all.</li>
 *   <li>Rows are emitted one per CRQ_CAB_SERVICE_TBL row joined to the service
 *       master, so a service with several rows repeats, and a service whose
 *       code is no longer in the master is dropped entirely - which is why this
 *       result set can come back empty even for a CRQ that has services.</li>
 * </ul>
 * Both SPOC fields are frequently null: they are optional columns on
 * CRQ_CAB_SERVICE_TBL that only get filled in once someone records a contact.
 */
@Getter
@Setter
public class CrqServiceSpocDto {

    private String serviceCode;
    private String spocName;
    private String spocContact;
}
