package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.teammanagement.dto.CreateUserDropdownResponseDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelUserHierarchyDto;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Master/lookup data (dropdowns + org hierarchy) loaded ONCE per upload and
 * pre-indexed for O(1) case-insensitive membership checks, instead of being
 * re-fetched or linearly scanned for every row.
 */
public final class ExcelMasterDataCache {

    private final Set<String> employmentTypes;
    private final Set<String> vendorCompanies;
    private final Set<String> designations;
    private final Set<String> jobLevels;
    private final Set<String> officeLocations;
    private final Set<String> deviceVendorCapabilities;
    private final Set<String> roleCodes;
    private final Set<String> genders;

    private final Map<String, Set<String>> verticalToFunctions;
    private final Map<String, Set<String>> functionToDomains;
    private final Map<String, Set<String>> domainToSubDomains;

    private ExcelMasterDataCache(
            Set<String> employmentTypes, Set<String> vendorCompanies, Set<String> designations,
            Set<String> jobLevels, Set<String> officeLocations, Set<String> deviceVendorCapabilities,
            Set<String> roleCodes, Set<String> genders,
            Map<String, Set<String>> verticalToFunctions,
            Map<String, Set<String>> functionToDomains,
            Map<String, Set<String>> domainToSubDomains) {
        this.employmentTypes = employmentTypes;
        this.vendorCompanies = vendorCompanies;
        this.designations = designations;
        this.jobLevels = jobLevels;
        this.officeLocations = officeLocations;
        this.deviceVendorCapabilities = deviceVendorCapabilities;
        this.roleCodes = roleCodes;
        this.genders = genders;
        this.verticalToFunctions = verticalToFunctions;
        this.functionToDomains = functionToDomains;
        this.domainToSubDomains = domainToSubDomains;
    }

    public static ExcelMasterDataCache build(
            CreateUserDropdownResponseDto dropdowns,
            List<ExcelUserHierarchyDto> hierarchy,
            List<String> genderList) {

        Map<String, Set<String>> verticalToFunctions = new HashMap<>();
        Map<String, Set<String>> functionToDomains = new HashMap<>();
        Map<String, Set<String>> domainToSubDomains = new HashMap<>();

        for (ExcelUserHierarchyDto dto : hierarchy) {
            addChild(verticalToFunctions, dto.getVerticalName(), dto.getFunctionName());
            addChild(functionToDomains, dto.getFunctionName(), dto.getDomainName());
            addChild(domainToSubDomains, dto.getDomainName(), dto.getSubDomainName());
        }

        return new ExcelMasterDataCache(
                toLowerSet(dropdowns.getEmploymentTypes()),
                toLowerSet(dropdowns.getVendorCompanies()),
                toLowerSet(dropdowns.getDesignations()),
                toLowerSet(dropdowns.getJobLevels()),
                toLowerSet(dropdowns.getOfficeLocations()),
                toLowerSet(dropdowns.getDeviceVendorCapabilities()),
                toLowerSet(dropdowns.getRoleCode()),
                toLowerSet(genderList),
                verticalToFunctions,
                functionToDomains,
                domainToSubDomains);
    }

    private static void addChild(Map<String, Set<String>> parentToChildren, String parent, String child) {
        if (parent == null || child == null) return;
        parentToChildren
                .computeIfAbsent(normalize(parent), k -> new HashSet<>())
                .add(normalize(child));
    }

    private static Set<String> toLowerSet(List<String> values) {
        Set<String> set = new HashSet<>();
        if (values == null) return set;
        for (String v : values) {
            if (v != null) set.add(normalize(v));
        }
        return set;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isEmploymentType(String value)          { return contains(employmentTypes, value); }
    public boolean isVendorCompany(String value)            { return contains(vendorCompanies, value); }
    public boolean isDesignation(String value)               { return contains(designations, value); }
    public boolean isJobLevel(String value)                  { return contains(jobLevels, value); }
    public boolean isOfficeLocation(String value)             { return contains(officeLocations, value); }
    public boolean isDeviceVendorCapability(String value)     { return contains(deviceVendorCapabilities, value); }
    public boolean isRoleCode(String value)                   { return contains(roleCodes, value); }
    public boolean isGender(String value)                     { return contains(genders, value); }

    public boolean isVertical(String vertical) {
        return vertical != null && verticalToFunctions.containsKey(normalize(vertical));
    }

    public boolean isFunctionOfVertical(String vertical, String function) {
        return isChildOf(verticalToFunctions, vertical, function);
    }

    public boolean isDomainOfFunction(String function, String domain) {
        return isChildOf(functionToDomains, function, domain);
    }

    public boolean isSubDomainOfDomain(String domain, String subDomain) {
        return isChildOf(domainToSubDomains, domain, subDomain);
    }

    private boolean isChildOf(Map<String, Set<String>> parentToChildren, String parent, String child) {
        if (parent == null || child == null) return false;
        Set<String> children = parentToChildren.get(normalize(parent));
        return children != null && children.contains(normalize(child));
    }

    private static boolean contains(Set<String> set, String value) {
        return value != null && set.contains(normalize(value));
    }
}
