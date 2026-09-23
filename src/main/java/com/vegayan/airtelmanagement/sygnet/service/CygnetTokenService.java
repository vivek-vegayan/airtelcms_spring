package com.vegayan.airtelmanagement.sygnet.service;

import com.vegayan.airtelmanagement.common.util.SslWebClientUtil;
import com.vegayan.airtelmanagement.sygnet.dto.CygnetTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;


@Service
public class CygnetTokenService {
    @Value("${cygnet.env}")
    private String cygnetEnv;

    @Value("${cygnet.sit-url}")
    private String cygnetSitUrl;

    @Value("${cygnet.prod-url}")
    private String cygnetProdUrl;

    private static final String TOKEN_PATH = "/rest/token/cygnettoken/gettokenvalue";
    private static final String CYGNET_AUTH = "Basic authtoken:cftengyc";

    private final WebClient webClient;

    public CygnetTokenService() throws Exception {
        this.webClient = SslWebClientUtil.buildTrustAllWebClient(60000, 60000);
    }

    private String getBaseUrl() {
        return "prod".equalsIgnoreCase(cygnetEnv) ? cygnetProdUrl : cygnetSitUrl;
    }

    public String fetchCygnetToken() {
        URI tokenUri = UriComponentsBuilder
                .fromUriString(getBaseUrl())
                .path(TOKEN_PATH)
                .build()
                .toUri();

        CygnetTokenResponse response = webClient.get()
                .uri(tokenUri)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("cygnet-authentication", CYGNET_AUTH)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .map(body -> new RuntimeException("Cygnet API Error: " + body))
                )
                .bodyToMono(CygnetTokenResponse.class) // ✅ map JSON directly
                .block();

        if (response == null || response.token() == null || response.token().isBlank()) {
            throw new RuntimeException("Cygnet token not found in response");
        }

        return response.token();
    }
}
