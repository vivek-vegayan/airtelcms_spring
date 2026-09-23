package com.vegayan.airtelmanagement.dataagent.service;

import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.dataagent.dto.DataAgentQueryResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Proxies a natural-language question to the external Traffic QA server
 * (AppPropertiesConfig#getPYTHON_SERVER_URL, {@code /ask}). That service is
 * LLM-backed (its own default per-call budget is 120s, see its README's
 * QA_TIMEOUT), so the timeout here is set with headroom above that rather
 * than a typical fast-API value - a shorter timeout would abort perfectly
 * healthy slow queries.
 *
 * The response already comes back as {question, intent, sql, columns, rows,
 * row_count, summary, error} - rows as a list of column->value maps - so
 * this layer passes it through close to verbatim, only defending against a
 * legacy array-of-arrays "rows" shape some older reference code assumed.
 */
@Service
public class DataAgentQueryService extends BaseService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(150);

    @SuppressWarnings("unchecked")
    public DataAgentQueryResponse ask(String question) {
        Map<String, Object> requestBody = Map.of("question", question, "summarize", true);

        Map<String, Object> body;
        try {
            body = webClient.post()
                    .uri(config.getPYTHON_SERVER_URL() + "/ask")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("Data Agent server returned an error")
                                    .flatMap(msg -> Mono.error(new BusinessException(msg))))
                    .bodyToMono(Map.class)
                    .timeout(REQUEST_TIMEOUT)
                    // Only network-level failures are worth one retry - not timeouts,
                    // which would just double an already-long LLM wait.
                    .retryWhen(Retry.backoff(1, Duration.ofSeconds(1))
                            .filter(ex -> ex instanceof WebClientRequestException))
                    .block();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw translateFailure(e, "ask");
        }

        if (body == null) {
            throw new BusinessException("Empty response from the Data Agent server.");
        }

        return toResponse(question, body);
    }

    private BusinessException translateFailure(Exception e, String call) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof BusinessException be) {
            return be;
        }
        if (cause instanceof TimeoutException) {
            LOGGER.warn("Data Agent /{} timed out", call);
            return new BusinessException("The Data Agent server took too long to respond. Please try again.");
        }
        if (cause instanceof WebClientRequestException) {
            LOGGER.warn("Data Agent /{} unreachable: {}", call, cause.getMessage());
            return new BusinessException("Unable to reach the Data Agent server. It may be temporarily down.");
        }
        LOGGER.error("Data Agent /{} call failed: {}", call, cause.getMessage(), cause);
        return new BusinessException("Something went wrong while talking to the Data Agent server.");
    }

    @SuppressWarnings("unchecked")
    private DataAgentQueryResponse toResponse(String question, Map<String, Object> body) {
        List<String> columns = body.get("columns") instanceof List<?> c
                ? (List<String>) c
                : List.of();

        List<Map<String, Object>> rows = toRowMaps(body.get("rows"), columns);

        Object rowCountObj = body.get("row_count");
        Integer rowCount = rowCountObj instanceof Number n ? n.intValue() : rows.size();

        return DataAgentQueryResponse.builder()
                .question(question)
                .intent(blankToNull(body.get("intent")))
                .sql(blankToNull(body.get("sql")))
                .columns(columns)
                .rows(rows)
                .rowCount(rowCount)
                .summary(blankToNull(body.get("summary")))
                .error(blankToNull(body.get("error")))
                .build();
    }

    /** rows is normally already a list of maps; a legacy array-of-arrays shape is reshaped using columns as a fallback. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toRowMaps(Object rowsObj, List<String> columns) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!(rowsObj instanceof List<?> rawRows)) {
            return rows;
        }

        for (Object rawRow : rawRows) {
            if (rawRow instanceof Map<?, ?> rowMap) {
                rows.add((Map<String, Object>) rowMap);
            } else if (rawRow instanceof List<?> rowValues) {
                Map<String, Object> reshaped = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    reshaped.put(columns.get(i), i < rowValues.size() ? rowValues.get(i) : null);
                }
                rows.add(reshaped);
            }
        }
        return rows;
    }

    private static String blankToNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
