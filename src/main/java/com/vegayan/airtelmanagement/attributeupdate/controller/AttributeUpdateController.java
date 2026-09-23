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


    @GetMapping("/details")
    public AttributeUpdateDetailsDto getDetails(
            @RequestParam String crqNo,
            @RequestParam String cmsStage)   {
        return attributeUpdateService.getDetails(crqNo, cmsStage);
    }


    @GetMapping("/dropdown/impl-company")
    public List<String> getImplCompanies() {
        return attributeUpdateService.getImplCompanies();
    }


    @GetMapping("/dropdown/impl-organization")
    public List<String> getImplOrganizations(@RequestParam String company) {
        return attributeUpdateService.getImplOrganizations(company);
    }


    @GetMapping("/dropdown/impl-group")
    public List<String> getImplGroups(
            @RequestParam String company,
            @RequestParam String organization) {
        return attributeUpdateService.getImplGroups(company, organization);
    }


    @PostMapping("/save")
    public ResponseEntity<AttributeUpdateSaveResponseDto> save(
            @RequestBody AttributeUpdateSaveRequestDto request) {
        try {
            return ResponseEntity.ok(attributeUpdateService.saveAttributes(request));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AttributeUpdateSaveResponseDto.builder()
                            .status("Error")
                            .message(e.getMessage())
                            .sections(List.of())
                            .build());
        }
    }
}
