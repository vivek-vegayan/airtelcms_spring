package com.vegayan.airtelmanagement.dataagent.service;

import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Proxies per-response feedback (thumbs/rating 1-5 + optional comment) to the
 * external Traffic QA server (AppPropertiesConfig#getPYTHON_SERVER_URL,
 * {@code /feedback}). Unlike /ask this call does no LLM work, so a much
 * shorter timeout than DataAgentQueryService's is appropriate.
 */
@Service
public class DataAgentFeedbackService extends BaseService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    @SuppressWarnings("unchecked")
    public Map<String, Object> submitFeedback(String requestId, String panelId, Integer rating, String comment) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("request_id", requestId);
        requestBody.put("panel_id", panelId);
        requestBody.put("rating", rating);
        requestBody.put("comment", comment);

        try {
            Map<String, Object> response = webClient.post()
                    .uri(config.getPYTHON_SERVER_URL() + "/feedback")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class)
                                    .defaultIfEmpty("Data Agent server returned an error")
                                    .flatMap(msg -> Mono.error(new BusinessException(msg))))
                    .bodyToMono(Map.class)
                    .timeout(REQUEST_TIMEOUT)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                            .filter(ex -> ex instanceof WebClientRequestException))
                    .block();
            return response == null ? Map.of() : response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof BusinessException be) {
                throw be;
            }
            if (cause instanceof TimeoutException) {
                throw new BusinessException("The Data Agent server took too long to respond. Please try again.");
            }
            if (cause instanceof WebClientRequestException) {
                throw new BusinessException("Unable to reach the Data Agent server. It may be temporarily down.");
            }
            LOGGER.error("Data Agent /feedback call failed: {}", cause.getMessage(), cause);
            throw new BusinessException("Something went wrong while sending feedback.");
        }
    }
}
