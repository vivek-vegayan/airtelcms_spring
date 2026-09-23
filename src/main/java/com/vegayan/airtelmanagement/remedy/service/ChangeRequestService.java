package com.vegayan.airtelmanagement.remedy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.exception.RemedyApiException;
import com.vegayan.airtelmanagement.common.service.CommonService;
import com.vegayan.airtelmanagement.common.util.SslWebClientUtil;
import com.vegayan.airtelmanagement.remedy.dto.RemedyChangeRequest;
import com.vegayan.airtelmanagement.remedy.dto.RemedyErrorFullDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChangeRequestService {

    ObjectMapper mapper = new ObjectMapper();
    private static final Logger changeRequest = LoggerFactory.getLogger("Change_Request_Logger");

    private final  RemedyTokenService remedyTokenService;

    private final CommonService commonService;

    protected JdbcTemplate jdbcTemplateTwo;

    @Value("${remedy.env}")
    private String remedyEnv;

    @Value("${remedy.sit-url}")
    private String remedySitUrl;

    @Value("${remedy.prod-url}")
    private String remedyProdUrl;

    @Value("${change_request.sit-api-key}")
    private String sitApiKey;

    @Value("${change_request.prod-api-key}")
    private String prodApiKey;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        try {
            this.webClient = SslWebClientUtil.buildTrustAllWebClient(60000, 60000);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize WebClient", e);
        }
    }

    private String getRemedyApiKey() {
        return "prod".equalsIgnoreCase(remedyEnv) ? prodApiKey : sitApiKey;
    }

    private String getRemedyBaseUrl() {
        return "prod".equalsIgnoreCase(remedyEnv) ? remedyProdUrl : remedySitUrl;
    }

    // ===================== MAIN METHOD =====================

    @LogType("Change_Request_Logger")
    public String remedyChangeRequest(RemedyChangeRequest requestPayload) {

        try {

            Map<String, Object> rawValues = requestPayload.getRequestData();


            // 2. TRANSFORM (internal → Helix field mapping)
            Map<String, Object> helixValues = transform(rawValues);

            // 3. WRAP payload as Helix expects
            Map<String, Object> finalPayload = new LinkedHashMap<>();
            finalPayload.put("values", helixValues);

            changeRequest.info(
                    "[Remedy Change Request] Final payload JSON: {}",
                    commonService.prettyPrintJson(finalPayload)
            );

            // 4. CALL HELIX
            return callRemedy(finalPayload);

        } catch (BusinessException | RemedyApiException ex) {
            throw ex;
        } catch (Exception ex) {
            changeRequest.error(ex.getMessage());
            throw new BusinessException(ex.getMessage());
        }
    }


    private void require(Map<String, Object> values, String key) {

        Object val = values.get(key);

        if (val == null || val.toString().isEmpty()) {
            throw new BusinessException(key + " is required for this stage");
        }
    }

    // ===================== TRANSFORM =====================

    private static final Map<String, String> KEY_MAPPING = Map.of(
            "changeRequestStatus", "Change Request Status",
            "infrastructureChangeId", "Infrastructure Change ID",
            "businessJustification", "Business Justification",
            "actualStartDate", "Actual Start Date",
            "actualEndDate", "Actual End Date",
            "performanceRating", "Performance Rating"
    );

    private Map<String, Object> transform(Map<String, Object> input) {

        Map<String, Object> output = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : input.entrySet()) {

            String key = entry.getKey();
            Object value = entry.getValue();

            String mappedKey = KEY_MAPPING.getOrDefault(key, key);

            output.put(mappedKey, value);
        }

        return output;
    }


    private String callRemedy(Map<String, Object> finalPayload) {

        URI baseUri = URI.create(getRemedyBaseUrl());

        URI uri = UriComponentsBuilder
                .fromUri(baseUri)
                .path("/api/arsys/v1/entry/ARTL:CHG:Change_Interface")
                .queryParam("fields", "values(Request ID)")
                .build()
                .toUri();

        changeRequest.info("[Remedy Change Request] Final URI: {}", uri);
        String apiKey = getRemedyApiKey();
        String remedyToken = remedyTokenService.fetchRemedyToken();

        // 1. Capture the response in a variable instead of returning it directly
        String responseBody = webClient.post()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .headers(h -> {
                    h.set("api-key", apiKey);
                    h.set("Authorization", remedyToken);
                    h.set("Cookie", "AR-JWT=" + remedyToken);
                })
                .bodyValue(finalPayload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(new ParameterizedTypeReference<List<RemedyErrorFullDto>>() {
                                })
                                .flatMap(errors -> {

                                    RemedyErrorFullDto error = errors.isEmpty()
                                            ? null
                                            : errors.get(0);

                                    String message = error != null
                                            ? error.getMessageAppendedText()
                                            : "Unknown Remedy error";

                                    Integer code = error != null
                                            ? error.getMessageNumber()
                                            : null;

                                    changeRequest.error("Remedy Change Request Error : {}", message);

                                    return Mono.error(new RemedyApiException(message, code));
                                })
                )
                .bodyToMono(String.class)
                .block();

        // --- WHAT I CHANGED STARTS HERE ---

        // 2. Validate it's not empty
        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("Empty response from Remedy API");
        }

        // 3. Pretty-print the response to the logs
        try {
            ObjectMapper mapper = new ObjectMapper();
            changeRequest.info(
                    "[Remedy Change Request] Response: \n{}",
                    mapper.readTree(responseBody).toPrettyString()
            );
        } catch (Exception e) {
            // Fallback: If Remedy returns plain text instead of JSON, just log the raw string
            changeRequest.info("[Remedy Change Request] Raw Response: {}", responseBody);
        }

        // --- WHAT I CHANGED ENDS HERE ---

        return responseBody;
    }

}
