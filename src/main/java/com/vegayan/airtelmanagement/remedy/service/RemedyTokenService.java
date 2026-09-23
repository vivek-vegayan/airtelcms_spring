package com.vegayan.airtelmanagement.remedy.service;

import com.vegayan.airtelmanagement.common.util.SslWebClientUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;


@Service
public class RemedyTokenService {

//    @Value("${remedy.env}")
//    private String remedyEnv;
//
//    @Value("${airtel_login.sit-api-key}")
//    private String airtelLoginSitKey;
//
//    @Value("${airtel_login.prod-api-key}")
//    private String airtelLoginProdKey;
//
//    @Value("${remedy.sit-url}")
//    private String remedySitUrl;
//
//    @Value("${remedy.prod-url}")
//    private String remedyProdUrl;
//
//
//    @Value("${remedy.user}")
//    private String user;
//
//
//    @Value("${remedy.pass-sit}")
//    private String remedyPassSit;
//
//    @Value("${remedy.pass-prod}")
//    private String remedyPassProd;
//
//    private String getRemedyApiKey() {
//        return "prod".equalsIgnoreCase(remedyEnv) ? airtelLoginProdKey : airtelLoginSitKey;
//    }
//
//    private String getRemedyBaseUrl() {
//        return "prod".equalsIgnoreCase(remedyEnv) ? remedyProdUrl : remedySitUrl;
//    }
//
//    private String getRemedyPassword() {
//        return "prod".equalsIgnoreCase(remedyEnv)
//                ? remedyPassProd
//                : remedyPassSit;
//    }
//
//    private static final Logger LOGGER = Logger.getLogger(RemedyTokenService.class.getName());
//
//    private final WebClient webClient;
//
//    public RemedyTokenService() throws Exception {
//        this.webClient = SslWebClientUtil.buildTrustAllWebClient(120000, 120000);
//    }
//
//    public String fetchRemedyToken() {
//        try {
//            LOGGER.info("Fetching Remedy API token...");
//
//            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
//            formData.add("username", user);
//            formData.add("password", getRemedyPassword());
//
//            String apiKey = getRemedyApiKey();
//            URI baseUri = URI.create(getRemedyBaseUrl());
//
//            URI tokenUri = UriComponentsBuilder
//                    .fromUri(baseUri)     // ✅ same modern baseUri approach
//                    .path("/api/jwt/login")
//                    .build()
//                    .toUri();
//
//            String responseBody = this.webClient.post()
//                    .uri(tokenUri)
//                    .header("api-key", apiKey)
//                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
//                    .accept(MediaType.TEXT_PLAIN, MediaType.ALL)
//                    .body(BodyInserters.fromFormData(formData))
//                    .retrieve()
//                    .onStatus(HttpStatusCode::isError, clientResponse ->
//                            clientResponse.bodyToMono(String.class)
//                                    .map(errorBody -> new RuntimeException("Token API Error: " + errorBody))
//                    )
//                    .bodyToMono(String.class)
//                    .block();   // keep blocking – your service is synchronous
//
//
//            LOGGER.info(() -> "Token API raw response: " + responseBody);
//
//            if (responseBody != null && !responseBody.isBlank()) {
//                String token = responseBody.replace("\"", "").trim();
//                LOGGER.info("Remedy token fetched successfully.");
//                return token;
//            } else {
//                throw new RuntimeException("Failed to fetch Remedy token: Empty response body");
//            }
//
//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Error fetching Remedy token", e);
//            throw new RuntimeException("Failed to fetch Remedy token", e);
//        }
//    }

    @Value("${remedy.env}")
    private String remedyEnv;

    @Value("${airtel_login.sit-api-key}")
    private String airtelLoginSitKey;

    @Value("${airtel_login.prod-api-key}")
    private String airtelLoginProdKey;

    @Value("${remedy.sit-url}")
    private String remedySitUrl;

    @Value("${remedy.prod-url}")
    private String remedyProdUrl;


    @Value("${remedy.user}")
    private String user;


    @Value("${remedy.pass-sit}")
    private String remedyPassSit;

    @Value("${remedy.pass-prod}")
    private String remedyPassProd;

    private String getRemedyApiKey() {
        return "prod".equalsIgnoreCase(remedyEnv) ? airtelLoginProdKey : airtelLoginSitKey;
    }

    private String getRemedyBaseUrl() {
        return "prod".equalsIgnoreCase(remedyEnv) ? remedyProdUrl : remedySitUrl;
    }

    private String getRemedyPassword() {
        return "prod".equalsIgnoreCase(remedyEnv)
                ? remedyPassProd
                : remedyPassSit;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RemedyTokenService.class);

    private final WebClient webClient;

    // --- MODERN CACHE VARIABLES ---
    private String cachedToken = null;
    private Instant tokenExpiryTime = Instant.MIN;

    // Using modern Duration API instead of raw math calculations
    private static final Duration TOKEN_VALIDITY = Duration.ofHours(7);

    public RemedyTokenService() throws Exception {
        this.webClient = SslWebClientUtil.buildTrustAllWebClient(120000, 120000);
    }

    public synchronized String fetchRemedyToken() {

        // 1. Use modern Instant API for time comparison
        if (cachedToken != null && Instant.now().isBefore(tokenExpiryTime)) {
            return cachedToken;
        }

        try {
            LOGGER.info("No valid cached token found. Fetching new Remedy API token...");

            // 2. Use 'var' for local variable type inference (Java 10+)
            var formData = new LinkedMultiValueMap<String, String>();
            formData.add("username", user);
            formData.add("password", getRemedyPassword());

            var apiKey = getRemedyApiKey();
            var baseUri = URI.create(getRemedyBaseUrl());

            var tokenUri = UriComponentsBuilder
                    .fromUri(baseUri)
                    .path("/api/jwt/login")
                    .build()
                    .toUri();

            var responseBody = this.webClient.post()
                    .uri(tokenUri)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.TEXT_PLAIN, MediaType.ALL)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(errorBody -> new RuntimeException("Token API Error: " + errorBody))
                    )
                    .bodyToMono(String.class)
                    .block();

            // 3. String.isBlank() is a Java 11+ feature (which was already in your code, nice job!)
            if (responseBody != null && !responseBody.isBlank()) {

                // Update cache using Instant API
                this.cachedToken = responseBody.replace("\"", "").trim();
                this.tokenExpiryTime = Instant.now().plus(TOKEN_VALIDITY);

                LOGGER.info("Remedy token fetched and cached successfully for the next 7 hours.");
                return this.cachedToken;
            } else {
                throw new RuntimeException("Failed to fetch Remedy token: Empty response body");
            }

        } catch (Exception e) {
            this.cachedToken = null;
            this.tokenExpiryTime = Instant.MIN;
            LOGGER.error("Error fetching Remedy token", e);
            throw new RuntimeException("Failed to fetch Remedy token", e);
        }
    }

}
