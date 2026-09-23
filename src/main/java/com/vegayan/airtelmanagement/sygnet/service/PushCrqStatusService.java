package com.vegayan.airtelmanagement.sygnet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.util.SslWebClientUtil;
import com.vegayan.airtelmanagement.sygnet.dto.PushCrqPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;


import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public class PushCrqStatusService extends BaseService {

    @Value("${cygnet.env}")
    private String cygnetEnv;

    @Value("${cygnet.sit-url}")
    private String cygnetSitUrl;

    @Value("${cygnet.prod-url}")
    private String cygnetProdUrl;

    private static final String CYGNET_PUSH_PATH = "/rest/changerequest/crqrest/sendCRQStatus";

    private final WebClient webClient;
    private final CygnetTokenService cygnetTokenService;
    private final ObjectMapper objectMapper;

    public PushCrqStatusService(CygnetTokenService cygnetTokenService, ObjectMapper objectMapper) throws Exception {
        this.webClient = SslWebClientUtil.buildTrustAllWebClient(10000, 120000);
        this.cygnetTokenService = cygnetTokenService;
        this.objectMapper = objectMapper;
    }

    private String getCygnetBaseUrl() {
        return "prod".equalsIgnoreCase(cygnetEnv) ? cygnetProdUrl : cygnetSitUrl;
    }


    public void pushCrqStatusToCygnet(String crqNo, String planNumber, String taskNumber, String status) {
        try {
            cancelCrqLog.info("[CYGNET] Preparing payload for CRQ {}", crqNo);

            PushCrqPayload payload = new PushCrqPayload(crqNo, planNumber, taskNumber, status);
            String jsonPayload = objectMapper.writeValueAsString(payload);
            cancelCrqLog.info("[CYGNET] Payload: {}", jsonPayload);

            String cygnetToken = cygnetTokenService.fetchCygnetToken();

            URI cygnetUri = UriComponentsBuilder
                    .fromUriString(getCygnetBaseUrl())
                    .path(CYGNET_PUSH_PATH)
                    .build()
                    .toUri();

            String responseText = webClient.post()
                    .uri(cygnetUri)
                    .headers(h -> {
                        h.remove(HttpHeaders.CONTENT_TYPE);
                        h.add(HttpHeaders.CONTENT_TYPE, "text/plain");
                        h.add("auth-token", cygnetToken);
                    })
                    .bodyValue(jsonPayload.getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            cancelCrqLog.info("[CYGNET] Response for CRQ {}: {}", crqNo, responseText);

        } catch (Exception e) {
            cancelCrqLog.error("[CYGNET] Push failed for CRQ {}: {}", crqNo, e.getMessage(), e);
            throw new RuntimeException("Failed to push CRQ status to Cygnet", e);
        }
    }
}
