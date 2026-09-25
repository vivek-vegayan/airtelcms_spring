package com.vegayan.airtelmanagement.sygnet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.util.ProcedureCallFormatter;
import com.vegayan.airtelmanagement.common.util.SslWebClientUtil;
import com.vegayan.airtelmanagement.sygnet.dto.PlanFetchRequest;
import com.vegayan.airtelmanagement.sygnet.dto.PlanFetchResultDto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Fetches a plan from Cygnet (fetchPlanEquipmentAndLinkDetails) and fills
 * NodeName / NameInterfacePair of CRQ_VALIDATION_DETAILS_TBL.
 *
 * Output format:
 *   NodeName           NODE_A,NODE_B,NODE_C
 *   NameInterfacePair  NODE_A$Bundle-Ether56,NODE_B$BS:MCIPS300C 25GE-ETY Port 22
 *
 * Rules:
 *   - nodes come from equipmentData[].neLabel and linkSummary[] A/Z end neLabel
 *   - pairs come from linkSummary only (equipmentData has no interface name)
 *   - interface "DUMMY" is dropped, but its node is still kept
 *   - values are trimmed, repeated spaces collapsed to one (internal spaces
 *     are kept), "" / "-" / null are ignored
 *   - output is sorted and de-duplicated
 *
 * The response is read as a JSON tree, not a fixed DTO, so any response
 * shape is handled: missing or null arrays, missing/null/numeric fields,
 * extra fields and non-SUCCESS replies all go through the same code path.
 */
@Service
public class CygnetNewPlanDataAPIService extends BaseService {

    private static final Logger cygnetPlanDataAPI = LoggerFactory.getLogger("Cygnet_Plan_Data_API");

    private static final String PLAN_PATH = "/rest/changerequest/crqrest/fetchPlanEquipmentAndLinkDetails";
    private static final String DUMMY = "DUMMY";

    /** WebClient's default in-memory limit is 256 KB - too small for a large plan. */
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    /** Whitespace incl. non-breaking space, which String.trim()/\s miss. */
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u2007\\u202F]+");

    /** Node / interface field names for the two ends of a link. */
    private static final String[][] LINK_ENDS = {
            {"aEndNeLabel", "aEndPtpMoName"},
            {"zEndNeLabel", "zEndPtpMoName"}
    };

    private static final String RAW_JSON_PROC = "CALL insert_crq_plan_raw_json(?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPSERT_PROC = "CALL upsert_crq_validation_from_plan(?, ?, ?, ?)";

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
            this.cygnetWebClient = SslWebClientUtil.buildTrustAllWebClient(60000, 60000)
                    .mutate()
                    .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize WebClient", e);
        }
    }

    private String getCygnetBaseUrl() {
        return "prod".equalsIgnoreCase(cygnetEnv) ? cygnetProdUrl : cygnetSitUrl;
    }

    @LogType("Cygnet_Plan_Data_API")
    public PlanFetchResultDto fetchAndSavePlan(PlanFetchRequest request) {
        if (request == null) {
            throw new BusinessException("CRQ Number and Plan Number are required.");
        }
        String crqNo = require(request.crqNo(), "CRQ Number");
        String planNumber = require(request.planNumber(), "Plan Number");

        // Step 1 : Call Cygnet (keep raw body so we store exactly what was sent)
        String rawJson = callPlanApi(planNumber);

        // Step 2 : Parse
        JsonNode root = parseJson(rawJson, planNumber);
        String status = text(root, "status");
        String message = text(root, "message");
        String errorCode = text(root, "errorCode");
        JsonNode data = root.path("data");

        // Step 3 : Save raw payload - also for a non-SUCCESS reply, so a
        //          failed fetch can be checked later
        Object[] rawArgs = {
                crqNo, planNumber, status, message, errorCode, rawJson,
                items(data.path("equipmentData")).size(),
                items(data.path("linkSummary")).size()
        };
        // Full, runnable copy of the call. The automatic SQL_PROC log line
        // cuts arguments over 256 chars, so that one cannot be re-run.
        cygnetPlanDataAPI.info("[Cygnet Plan Fetch] {}", ProcedureCallFormatter.renderPreparedFull(RAW_JSON_PROC, rawArgs));
        databaseUtils.executeProcedureWithError(jdbcTemplateTwo, RAW_JSON_PROC, rawArgs);

        if (!"SUCCESS".equalsIgnoreCase(status)) {
            throw new BusinessException("Cygnet returned status=" + status
                    + ", errorCode=" + errorCode
                    + ", message=" + message + " for plan " + planNumber);
        }

        // Guard against storing another plan's data under this CRQ
        String returnedPlan = text(data, "planNumber");
        if (returnedPlan != null && !returnedPlan.equalsIgnoreCase(planNumber)) {
            throw new BusinessException("Cygnet returned plan " + returnedPlan
                    + " but plan " + planNumber + " was requested.");
        }

        // Step 4 : Extract nodes and node$interface pairs
        PlanFetchResultDto result = extractNodesAndPairs(root);
        result.setCrqNo(crqNo);
        result.setPlanNumber(planNumber);

        // Step 5 : Save into CRQ_VALIDATION_DETAILS_TBL (one row per CRQ + plan).
        //          Plan_Id, Task_Id, Domain and Plan_Activity_Details are
        //          resolved inside the procedure from the plan number.
        databaseUtils.executeProcedureWithError(
                jdbcTemplateTwo, UPSERT_PROC,
                crqNo, planNumber, result.getNodeName(), result.getNameInterfacePair());

        if (result.getNodeCount() == 0) {
            cygnetPlanDataAPI.warn("[Cygnet Plan Fetch] Plan {} (crq {}) produced no nodes; equipment={} links={}",
                    planNumber, crqNo, result.getEquipmentCount(), result.getLinkCount());
        }
        cygnetPlanDataAPI.info("[Cygnet Plan Fetch] Plan {} (crq {}) -> {} nodes, {} pairs ({} DUMMY interfaces dropped)",
                planNumber, crqNo, result.getNodeCount(), result.getPairCount(), result.getDummySkipped());

        return result;
    }

    /**
     * Builds NodeName / NameInterfacePair from a plan response.
     * No DB or network access - safe to call from tests or for re-processing
     * a stored payload.
     */
    public PlanFetchResultDto extractNodesAndPairs(JsonNode root) {
        Set<String> nodes = new TreeSet<>();
        Set<String> pairs = new TreeSet<>();
        int dummySkipped = 0;

        JsonNode data = root == null ? null : root.path("data");
        List<JsonNode> equipmentList = items(data == null ? null : data.path("equipmentData"));
        List<JsonNode> linkList = items(data == null ? null : data.path("linkSummary"));

        // equipmentData : node only
        for (JsonNode equipment : equipmentList) {
            String node = text(equipment, "neLabel");
            if (node != null) {
                nodes.add(node);
            }
        }

        // linkSummary : A end and Z end, each a node + interface
        for (JsonNode link : linkList) {
            for (String[] end : LINK_ENDS) {
                String node = text(link, end[0]);
                if (node == null) {
                    continue;
                }
                nodes.add(node);                        // node kept regardless

                String iface = text(link, end[1]);
                if (iface == null) {
                    continue;
                }
                if (DUMMY.equalsIgnoreCase(iface)) {
                    dummySkipped++;                     // node kept, interface dropped
                    continue;
                }
                pairs.add(node + "$" + iface);
            }
        }

        PlanFetchResultDto result = new PlanFetchResultDto();
        result.setNodeName(nodes.isEmpty() ? null : String.join(",", nodes));
        result.setNameInterfacePair(pairs.isEmpty() ? null : String.join(",", pairs));
        result.setNodeCount(nodes.size());
        result.setPairCount(pairs.size());
        result.setDummySkipped(dummySkipped);
        result.setEquipmentCount(equipmentList.size());
        result.setLinkCount(linkList.size());
        return result;
    }

    private String callPlanApi(String planNumber) {
        URI uri = UriComponentsBuilder
                .fromUriString(getCygnetBaseUrl())
                .path(PLAN_PATH)
                .build()
                .toUri();

        cygnetPlanDataAPI.info("[Cygnet Plan Data API] Final URI: {}", uri);

        String cygnetToken = cygnetTokenService.fetchCygnetToken();

        String body = cygnetWebClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("auth-token", cygnetToken)
                .bodyValue(Map.of("planNumber", planNumber))
                .exchangeToMono(response -> {
                    // 2xx and 4xx carry a JSON status/message we want to keep;
                    // 5xx means Cygnet itself is down.
                    if (response.statusCode().is5xxServerError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.bodyToMono(String.class);
                })
                .block();

        cygnetPlanDataAPI.info("[Cygnet Plan Data API] Response from Cygnet:\n{}",
                commonService.prettyPrintJson(body)
        );
        if (body == null || body.isBlank()) {
            throw new BusinessException("Empty response from Cygnet for plan " + planNumber);
        }
        return body;
    }

    private JsonNode parseJson(String rawJson, String planNumber) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("not a JSON object");
            }
            return root;
        } catch (Exception e) {
            cygnetPlanDataAPI.error("[Cygnet Plan Fetch] Unparseable response for plan {}: {}", planNumber, rawJson, e);
            throw new BusinessException("Invalid response from Cygnet for plan " + planNumber);
        }
    }

    /** Elements of a JSON array; empty for null / missing / non-array. */
    private static List<JsonNode> items(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> list = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            if (item != null && item.isObject()) {
                list.add(item);
            }
        }
        return list;
    }

    /** Normalised text of a field; null when missing, JSON null, object/array or blank. */
    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        if (!value.isValueNode() || value.isNull()) {
            return null;
        }
        return normalize(value.asText());
    }

    /** trim, collapse multiple spaces to one, "" and "-" become null. */
    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = SPACES.matcher(value).replaceAll(" ").trim();
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
