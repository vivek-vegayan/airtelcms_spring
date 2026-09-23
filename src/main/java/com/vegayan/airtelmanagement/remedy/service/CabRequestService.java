package com.vegayan.airtelmanagement.remedy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.util.SslWebClientUtil;
import com.vegayan.airtelmanagement.remedy.dto.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class CabRequestService extends BaseService {

    private static final Logger cabRequest = LoggerFactory.getLogger("Cab_Request_Logger");

    private final RemedyTokenService remedyTokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private String getRemedyApiKey() {
        return "prod".equalsIgnoreCase(remedyEnv) ? prodApiKey : sitApiKey;
    }

    private String getRemedyBaseUrl() {
        return "prod".equalsIgnoreCase(remedyEnv) ? remedyProdUrl : remedySitUrl;
    }

    private WebClient webClient;

    @PostConstruct
    public void init() {
        try {
            this.webClient = SslWebClientUtil.buildTrustAllWebClient(60000, 60000);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize WebClient", e);
        }
    }

    @LogType("Cab_Request_Logger")
    public String remedyCabRequest(RemedyCabRequestDto requestPayload) {

        try {

            cabRequest.info(
                    "[Remedy CAB Attribute] Sending payload to Remedy API"
            );

            cabRequest.info(
                    "[Remedy  CAB Attribute] Final payload JSON: {}",
                    commonService.prettyPrintJson(requestPayload)
            );

            URI baseUri = URI.create(getRemedyBaseUrl());

            URI uri = UriComponentsBuilder
                    .fromUri(baseUri)
                    .path("/api/arsys/v1/entry/ARTL:CHG:Change_Interface")
                    .queryParam("fields", "values(Request ID)")
                    .build()
                    .toUri();

            cabRequest.info("[Remedy CAB Attribute] Final URI: {}", uri);

            String apiKey = getRemedyApiKey();

            String remedyToken =
                    remedyTokenService.fetchRemedyToken();

            String response = webClient.post()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .headers(h -> {
                        h.set("api-key", apiKey);
                        h.set("Authorization", remedyToken);
                        h.set("Cookie", "AR-JWT=" + remedyToken);
                    })
                    .bodyValue(requestPayload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        cabRequest.error("Error Body = {}", body);
                                        return Mono.error(new RuntimeException(body));
                                    }))
                    .bodyToMono(String.class)
                    .block();


            cabRequest.info(
                    "[Remedy CAB Attribute 1] Response: {}",
                    response
            );
            if (response == null || response.isBlank()) {
                throw new RuntimeException("Empty response from Remedy API");
            }

            ObjectMapper mapper = new ObjectMapper();
            cabRequest.info(
                    "[Remedy CAB Attribute] Response: \n{}",
                    mapper.readTree(response).toPrettyString()
            );
            return response;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to call Remedy CAB Attribute",
                    e
            );
        }
    }

    @LogType("Cab_Request_Logger")
    public SpocUpdateChmResponse spocUpdateExternal(SpocUpdateDTO body) {

        try {
            cabRequest.info(
                    "call SP_UPDATE_SPOC('{}','{}','{}','{}','{}','{}');",
                    body.changeId(), body.crqCreationOLM(),body.spocOLM(),body.spocName(), body.spocContact(), body.feName()
            );

            String sql = "CALL SP_UPDATE_SPOC(?,?,?,?,?,?)";

            ApiResponse dbResponse = databaseUtils.executeProcedureForMessageV1(
                    jdbcTemplateTwo,
                    sql,
                    body.changeId(),
                    body.crqCreationOLM(),
                    body.spocOLM(),
                    body.spocName(),
                    body.spocContact(),
                    body.feName()
            );

            return new SpocUpdateChmResponse(
                    "SUCCESS",
                    dbResponse.message(),
                    body.changeId()
            );

        } catch (Exception e) {
            cabRequest.error(
                    "CHM procedure failed for changeId: {}. Message: {}",
                    body.changeId(),
                    e.getMessage()
            );

            return new SpocUpdateChmResponse(
                    "fail",
                    body.changeId(),
                    e.getMessage()
            );
        }
    }

}
