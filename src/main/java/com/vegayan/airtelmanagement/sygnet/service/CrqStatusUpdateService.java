package com.vegayan.airtelmanagement.sygnet.service;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.service.CommonService;
import com.vegayan.airtelmanagement.common.util.DateTimeUtils;
import com.vegayan.airtelmanagement.common.util.SslWebClientUtil;
import com.vegayan.airtelmanagement.sygnet.dto.CrqStatusUpdateDto;
import com.vegayan.airtelmanagement.sygnet.dto.CrqStatusUpdateResponseDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CrqStatusUpdateService extends BaseService {

    private final CommonService commonService;
    private static final Logger attributeRequestToCygnet = LoggerFactory.getLogger("Attribute_Request_To_Cygnet");

    private final CygnetTokenService cygnetTokenService;

    @Value("${cygnet.env}")
    private String cygnetEnv;

    @Value("${cygnet.sit-url}")
    private String cygnetSitUrl;

    @Value("${cygnet.prod-url}")
    private String cygnetProdUrl;


    private String getCygnetBaseUrl() {
        return "prod".equalsIgnoreCase(cygnetEnv) ? cygnetProdUrl : cygnetSitUrl;
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

    @LogType("Cygnet_Attribute_Request")
    public CrqStatusUpdateResponseDto crqStatusUpdate(String crqNo) {

        try {

            // Step 1 : Fetch data from DB
            CrqStatusUpdateDto requestPayload =     fetchCrqDetailsFromDb(crqNo);

            requestPayload.setScheduledStartDate(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getScheduledStartDate()));

            requestPayload.setScheduledEndDate(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getScheduledEndDate()));

            requestPayload.setActualStartDate(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getActualStartDate()));

            requestPayload.setActualEndDate(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getActualEndDate()));

            requestPayload.setCompletedDate(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getCompletedDate()));

            requestPayload.setCrqValidatedTime(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getCrqValidatedTime()));

            requestPayload.setImpactAnalysisDoneTime(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getImpactAnalysisDoneTime()));

            requestPayload.setMopCreatedByTime(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getMopCreatedByTime()));

            requestPayload.setMopValidatedByTime(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getMopValidatedByTime()));

            requestPayload.setCrqScheduledByTime(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getCrqScheduledByTime()));

            requestPayload.setPreCheckDoneTime(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getPreCheckDoneTime()));

            requestPayload.setPostCheckDoneTime(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getPostCheckDoneTime()));

            requestPayload.setChangeActivityDoneTime(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getChangeActivityDoneTime()));

            requestPayload.setCrqClosedByTime(DateTimeUtils.toRemedyIstFromString(
                    requestPayload.getCrqClosedByTime()));

            // Step 2 : Print EXACT JSON that will be sent to Cygnet
            attributeRequestToCygnet.info(
                    "[Cygnet Attribute Update] Final payload JSON: {}",
                    commonService.prettyPrintJson(requestPayload)
            );

            URI baseUri = URI.create(getCygnetBaseUrl());

            URI uri = UriComponentsBuilder
                    .fromUri(baseUri)
                    .path("/rest/changerequest/crqrest/updateCRQStatusFromVegayan")
                    .build()
                    .toUri();

            attributeRequestToCygnet.info("[Remedy CAB Attribute] Final URI: {}", uri);

            // Step 2 : Get token
            String cygnetToken = cygnetTokenService.fetchCygnetToken();

            // Step 3 : Send to Cygnet
            CrqStatusUpdateResponseDto response = webClient.post()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("auth-token", cygnetToken)
                    .bodyValue(requestPayload)
                    .exchangeToMono(clientResponse -> {
                        if (clientResponse.statusCode().is2xxSuccessful() || clientResponse.statusCode().is4xxClientError()) {
                            // Safely map both 200 OK and 400 Bad Request payloads to your DTO
                            return clientResponse.bodyToMono(CrqStatusUpdateResponseDto.class);
                        } else {
                            // 5xx Server Errors (like Cygnet being down) should still throw exceptions
                            return clientResponse.createException().flatMap(Mono::error);
                        }
                    })
                    .block();

            attributeRequestToCygnet.info("[Cygnet Attribute Update] Response from Cygnet:\n{}",
                    commonService.prettyPrintJson(response)
            );

            return response;

        } catch (Exception e) {

            attributeRequestToCygnet.error(
                    "[CRQ Status Update] Error while calling API",
                    e
            );

            throw new RuntimeException(
                    "Failed to call CRQ Status Update API",
                    e
            );
        }
    }

    @LogType("Crq_Status_Update_External")
    private CrqStatusUpdateDto fetchCrqDetailsFromDb(String crqNo) {
        String sql = "CALL Get_CRQ_Status_Update(?)";

        attributeRequestToCygnet.info("call Get_CRQ_Status_Update('{}');", crqNo);
        List<CrqStatusUpdateDto> result =
                databaseUtils.executeProcedureAndFetchObjects(
                        jdbcTemplateTwo,
                        sql,
                        CrqStatusUpdateDto.class,
                        crqNo
                );

        return result.stream()
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException(
                                "CRQ not found: " + crqNo));
    }
}
