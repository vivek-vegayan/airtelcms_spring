package com.vegayan.airtelmanagement.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;



import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommonService {

    private final ObjectMapper objectMapper;

    public static String formatProcedureCall(String procedureName, Object[] params) {

        String paramString = Arrays.stream(params)
                .map(p -> {
                    if (p == null) return "NULL";

                    if (p instanceof Number || p instanceof Boolean) {
                        return p.toString();
                    }

                    if (p instanceof LocalDate || p instanceof LocalDateTime) {
                        return "'" + p.toString() + "'";
                    }

                    return "'" + p.toString().replace("'", "''") + "'";
                })
                .collect(Collectors.joining(", "));

        return "CALL " + procedureName + "(" + paramString + ");";
    }

    public String prettyPrintJson(Object dto) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            return "Error serializing object: " + e.getMessage();
        }
    }


}
