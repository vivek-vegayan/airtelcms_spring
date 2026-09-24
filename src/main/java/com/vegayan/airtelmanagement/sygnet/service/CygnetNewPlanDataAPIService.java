package com.vegayan.airtelmanagement.sygnet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.util.SslWebClientUtil;
import com.vegayan.airtelmanagement.sygnet.dto.PlanDetailsResponse;
import com.vegayan.airtelmanagement.sygnet.dto.PlanFetchRequest;
import com.vegayan.airtelmanagement.sygnet.dto.PlanFetchResultDto;
import com.vegayan.airtelmanagement.sygnet.service.CygnetTokenService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Fetches a plan from Cygnet (fetchPlanEquipmentAndLinkDetails) and fills
 * NodeName / NameInterfacePair of CRQ_VALIDATION_DETAILS_TBL.
 *
 * Output format:
 *   NodeName           NODE_A,NODE_B,NODE_C
 *   NameInterfacePair  NODE_A$Bundle-Ether56,NODE_B$10GigE-9
 *
 * Rules:
 *   - nodes come from equipmentData[].neLabel and linkSummary[] A/Z end neLabel
 *   - pairs come from linkSummary only (equipmentData has no interface name)
 *   - interface "DUMMY" is dropped, but its node is still kept
 *   - values are trimmed, extra spaces collapsed, "" and "-" are ignored
 *   - output is sorted and de-duplicated
 */
@Service
public class CygnetNewPlanDataAPIService extends BaseService {

    private static final String PLAN_PATH = "/rest/changerequest/crqrest/fetchPlanEquipmentAndLinkDetails";
    private static final String DUMMY = "DUMMY";

    private final CygnetTokenService cygnetTokenService;
    private final ObjectMapper objectMapper;

    @Value("${cygnet.env}")
    private String cygnetEnv;

    @Value("${cygnet.sit-url}")
    private String cygnetSitUrl;

    @Value("${cygnet.prod-url}")
    private String cygnetProdUrl;

    private WebClient cygnetWebClient;

    public CygnetNewPlanDataAPIService(CygnetTokenService cygnetTokenService, ObjectMapper objectMapper) {
        this.cygnetTokenService = cygnetTokenService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            this.cygnetWebClient = SslWebClientUtil.buildTrustAllWebClient(60000, 60000);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize WebClient", e);
        }
    }

    private String getCygnetBaseUrl() {
        return "prod".equalsIgnoreCase(cygnetEnv) ? cygnetProdUrl : cygnetSitUrl;
    }

    public PlanFetchResultDto fetchAndSavePlan(PlanFetchRequest request) {
        if (request == null) {
            throw new BusinessException("CRQ Number and Plan Number are required.");
        }
        String crqNo = require(request.crqNo(), "CRQ Number");
        String planNumber = require(request.planNumber(), "Plan Number");

        // Step 1 : Call Cygnet (keep raw body so we store exactly what was sent)
        String rawJson = callPlanApi(planNumber);

        // Step 2 : Parse
        PlanDetailsResponse response;
        try {
            response = objectMapper.readValue(rawJson, PlanDetailsResponse.class);
        } catch (Exception e) {
            LOGGER.error("Unparseable plan response for plan {}: {}", planNumber, rawJson, e);
            throw new BusinessException("Invalid response from Cygnet for plan " + planNumber);
        }

        if (!response.isSuccess()) {
            throw new BusinessException("Cygnet returned status=" + response.getStatus()
                    + ", errorCode=" + response.getErrorCode()
                    + ", message=" + response.getMessage() + " for plan " + planNumber);
        }

        List<PlanDetailsResponse.Equipment> equipmentList =
                response.getData() == null ? null : response.getData().getEquipmentData();
        List<PlanDetailsResponse.Link> linkList =
                response.getData() == null ? null : response.getData().getLinkSummary();

        // Step 3 : Extract nodes and node$interface pairs
        Set<String> nodes = new TreeSet<>();
        Set<String> pairs = new TreeSet<>();
        int dummySkipped = 0;

        if (equipmentList != null) {
            for (PlanDetailsResponse.Equipment equipment : equipmentList) {
                if (equipment == null) continue;
                String node = normalize(equipment.getNeLabel());
                if (node != null) {
                    nodes.add(node);
                }
            }
        }

        if (linkList != null) {
            for (PlanDetailsResponse.Link link : linkList) {
                if (link == null) continue;
                if (addLinkEnd(nodes, pairs, link.getAEndNeLabel(), link.getAEndPtpMoName())) {
                    dummySkipped++;
                }
                if (addLinkEnd(nodes, pairs, link.getZEndNeLabel(), link.getZEndPtpMoName())) {
                    dummySkipped++;
                }
            }
        }

        String nodeName = nodes.isEmpty() ? null : String.join(",", nodes);
        String nameInterfacePair = pairs.isEmpty() ? null : String.join(",", pairs);

        // Step 4 : Save raw payload
        databaseUtils.executeProcedureWithError(
                jdbcTemplateTwo,
                "CALL insert_crq_plan_raw_json(?, ?, ?, ?, ?, ?, ?, ?)",
                crqNo, planNumber, response.getStatus(), response.getMessage(),
                response.getErrorCode(), rawJson,
                equipmentList == null ? 0 : equipmentList.size(),
                linkList == null ? 0 : linkList.size());

        // Step 5 : Save into CRQ_VALIDATION_DETAILS_TBL (one row per CRQ + plan).
        //          Plan_Id, Task_Id, Domain and Plan_Activity_Details are
        //          resolved inside the procedure from the plan number.
        databaseUtils.executeProcedureWithError(
                jdbcTemplateTwo,
                "CALL upsert_crq_validation_from_plan(?, ?, ?, ?)",
                crqNo, planNumber, nodeName, nameInterfacePair);

        if (nodes.isEmpty()) {
            LOGGER.warn("Plan {} (crq {}) produced no nodes; equipment={} links={}",
                    planNumber, crqNo,
                    equipmentList == null ? 0 : equipmentList.size(),
                    linkList == null ? 0 : linkList.size());
        }
        LOGGER.info("Plan {} -> {} nodes, {} pairs ({} DUMMY interfaces dropped)",
                planNumber, nodes.size(), pairs.size(), dummySkipped);

        PlanFetchResultDto result = new PlanFetchResultDto();
        result.setCrqNo(crqNo);
        result.setPlanNumber(planNumber);
        result.setNodeName(nodeName);
        result.setNameInterfacePair(nameInterfacePair);
        result.setNodeCount(nodes.size());
        result.setPairCount(pairs.size());
        result.setDummySkipped(dummySkipped);
        return result;
    }

    private String callPlanApi(String planNumber) {
        URI uri = UriComponentsBuilder
                .fromUriString(getCygnetBaseUrl())
                .path(PLAN_PATH)
                .build()
                .toUri();

        String cygnetToken = cygnetTokenService.fetchCygnetToken();

        LOGGER.info("[Cygnet Plan Fetch] POST {} planNumber={}", uri, planNumber);

        String body = cygnetWebClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("auth-token", cygnetToken)
                .bodyValue(Map.of("planNumber", planNumber))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (body == null || body.isBlank()) {
            throw new BusinessException("Empty response from Cygnet for plan " + planNumber);
        }
        return body;
    }

    /**
     * Adds one link end. The node is always kept; the pair is added only
     * when the interface is present and not DUMMY.
     *
     * @return true if a DUMMY interface was skipped
     */
    private boolean addLinkEnd(Set<String> nodes, Set<String> pairs, String rawNode, String rawInterface) {
        String node = normalize(rawNode);
        if (node == null) {
            return false;
        }
        nodes.add(node);

        String iface = normalize(rawInterface);
        if (iface == null) {
            return false;
        }
        if (DUMMY.equalsIgnoreCase(iface)) {
            return true;
        }
        pairs.add(node + "$" + iface);
        return false;
    }

    /** trim, collapse multiple spaces to one, "" and "-" become null. */
    private static String normalize(String value) {
        if (value == null) return null;
        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty() || "-".equals(cleaned)) {
            return null;
        }
        return cleaned;
    }

    private static String require(String value, String label) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new BusinessException(label + " is required.");
        }
        return trimmed;
    }
}
