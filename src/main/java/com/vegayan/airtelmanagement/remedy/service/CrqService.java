package com.vegayan.airtelmanagement.remedy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.remedy.dto.CancelCrqPayloadDto;
import com.vegayan.airtelmanagement.remedy.dto.RemedyErrorResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;



import java.net.URI;
import java.util.List;


@Service
public class CrqService extends BaseService{

    private final RemedyTokenService tokenService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public CrqService(
            RemedyTokenService tokenService,
            @Qualifier("trustAllWebClient") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.tokenService = tokenService;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }
    @Value("${remedy.env}")
    private String remedyEnv;

    @Value("${remedy.sit-url}")
    private String remedySitUrl;

    @Value("${remedy.prod-url}")
    private String remedyProdUrl;

    @Value("${cancel_crq/attachment.sit-api-key}")
    private String sitApiKey;

    @Value("${cancel_crq/attachment.prod-api-key}")
    private String prodApiKey;

    private String getRemedyApiKey() {
        return "prod".equalsIgnoreCase(remedyEnv) ? prodApiKey : sitApiKey;
    }

    private String getRemedyBaseUrl() {
        return "prod".equalsIgnoreCase(remedyEnv) ? remedyProdUrl : remedySitUrl;
    }


    public void callRemedyCancelCrqApi(
            String crqNo, String field1, String field3, String field4, String field5) {

        cancelCrqLog.info("[CANCEL CRQ] Preparing Remedy Cancel API request...");

        //  Build payload
        CancelCrqPayloadDto payload = new CancelCrqPayloadDto();
        payload.getValues().setInfrastructureChangeId(crqNo);
        payload.getValues().setField1(field1);
        payload.getValues().setField3(field3);
        payload.getValues().setField4(field4);
        payload.getValues().setField5(field5);


        // Log payload as JSON for debugging (safe, separate from sending)
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            cancelCrqLog.info("[CANCEL CRQ] entry payload:\n{}", payloadJson);
        } catch (Exception e) {
            cancelCrqLog.warn("[CANCEL CRQ] Failed to convert payload to JSON for logging", e);
        }

        //  Headers for multipart part
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_JSON);

        //  Direct DTO (no manual JSON)
        HttpEntity<CancelCrqPayloadDto> entryPart =
                new HttpEntity<>(payload, partHeaders);

        //  Multipart body
        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("entry", entryPart);

        //  Tokens & URL
        String token = tokenService.fetchRemedyToken();
        String apiKey = getRemedyApiKey();
        String baseUrl = getRemedyBaseUrl();

        URI remedyUri = UriComponentsBuilder
                .fromUri(URI.create(baseUrl))
                .path("/api/arsys/v1/entry/ARTL:HPD:CLA:Inbound_WorkLog_Staging")
                .build()
                .toUri();

        try {
            String response = webClient.post()
                    .uri(remedyUri)
                    .headers(h -> {
                        h.set("api-key", apiKey);
                        h.set(HttpHeaders.AUTHORIZATION, "AR-JWT " + token);
                        h.set(HttpHeaders.COOKIE, "AR-JWT=" + token);
                        h.setAccept(List.of(MediaType.APPLICATION_JSON));
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(multipartBody))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::handleRemedyCancelCrqError)
                    .bodyToMono(String.class)
                    .block();

            cancelCrqLog.info("[CANCEL CRQ] Remedy API Response:\n{}", response);

        } catch (RuntimeException e) {
            cancelCrqLog.error("[CANCEL CRQ] Remedy Cancel API failed: {}", e.getMessage());
            throw e;
        }
    }

    private Mono<? extends Throwable> handleRemedyCancelCrqError(ClientResponse response) {
        return response.bodyToMono(RemedyErrorResponseDto[].class)
                .flatMap(errors -> {
                    if (errors != null && errors.length > 0 &&
                            errors[0].getMessageAppendedText() != null) {

                        return Mono.error(new RuntimeException(errors[0].getMessageAppendedText()));
                    }

                    return Mono.error(new RuntimeException("Unknown Remedy error"));
                });
    }


}
