package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Result set 3 of sp_get_crq_journey_page - the CRQ's org scope, resolved from
 * CRQ_MASTER_TBL.domain_id / sub_domain_id against ORG_DOMAIN / ORG_SUB_DOMAIN.
 * Either name can be null when the CRQ carries an id that no longer resolves.
 */
@Getter
@Setter
public class CrqJourneyScopeDto {

    private String domainName;
    private String subDomainName;
}
