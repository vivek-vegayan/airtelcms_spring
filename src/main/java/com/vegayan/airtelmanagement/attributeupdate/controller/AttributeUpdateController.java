package com.vegayan.airtelmanagement.attributeupdate.controller;

import com.vegayan.airtelmanagement.attributeupdate.dto.AttributeUpdateDetailsDto;
import com.vegayan.airtelmanagement.attributeupdate.dto.AttributeUpdateSaveRequestDto;
import com.vegayan.airtelmanagement.attributeupdate.dto.AttributeUpdateSaveResponseDto;
import com.vegayan.airtelmanagement.attributeupdate.service.AttributeUpdateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/attributeupdate")
public class AttributeUpdateController {

    private final AttributeUpdateService attributeUpdateService;

    public AttributeUpdateController(AttributeUpdateService attributeUpdateService) {
        this.attributeUpdateService = attributeUpdateService;
    }

    /**
     * Latest saved Remedy / CAB / Cygnet attribute snapshot for one CRQ at
     * one CMS stage - a single combined round trip for all three sections
     * the Attribute Update dialog shows.
     */
    @GetMapping("/details")
    public AttributeUpdateDetailsDto getDetails(
            @RequestParam String crqNo,
            @RequestParam String cmsStage)   {
        return attributeUpdateService.getDetails(crqNo, cmsStage);
    }

    /**
     * Support company list (GET_IMPL_COMPANY_DROPDOWN) - level 1 of the Support
     * Company / Organization / Group Name+ cascade on the Attribute Update
     * dialog's Remedy section. One pool of support groups serves both the
     * Change Coordinator and the Change Implementer trio.
     */
    @GetMapping("/dropdown/impl-company")
    public List<String> getImplCompanies() {
        return attributeUpdateService.getImplCompanies();
    }

    /**
     * Support organizations valid for one company
     * (GET_IMPL_ORG_DROPDOWN) - level 2 of the cascade.
     */
    @GetMapping("/dropdown/impl-organization")
    public List<String> getImplOrganizations(@RequestParam String company) {
        return attributeUpdateService.getImplOrganizations(company);
    }

    /**
     * Support groups valid for one company + organization
     * (GET_IMPL_GROUP_DROPDOWN) - level 3 of the cascade.
     */
    @GetMapping("/dropdown/impl-group")
    public List<String> getImplGroups(
            @RequestParam String company,
            @RequestParam String organization) {
        return attributeUpdateService.getImplGroups(company, organization);
    }

    /**
     * Saves whichever of Remedy / CAB / Cygnet sections the client sends for
     * the current stage. Each section is inserted independently; partial
     * failures are reported in the response message rather than silently
     * dropped.
     */
    @PostMapping("/save")
    public ResponseEntity<AttributeUpdateSaveResponseDto> save(
            @RequestBody AttributeUpdateSaveRequestDto request) {
        try {
            return ResponseEntity.ok(attributeUpdateService.saveAttributes(request));
        } catch (RuntimeException e) {
            // Nothing ran far enough to produce per-section results (the request
            // itself blew up), so `sections` is empty and the client falls back
            // to the single message.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AttributeUpdateSaveResponseDto.builder()
                            .status("Error")
                            .message(e.getMessage())
                            .sections(List.of())
                            .build());
        }
    }
}
