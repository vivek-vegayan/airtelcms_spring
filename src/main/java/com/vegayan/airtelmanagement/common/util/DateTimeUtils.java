package com.vegayan.airtelmanagement.common.util;

import com.vegayan.airtelmanagement.common.service.BaseService;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateTimeUtils extends BaseService {

    /** The zone every wall-clock time in this system is written and read in. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final DateTimeFormatter OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    /** Remedy's own wire shape, offset included: "2026-09-24T05:19:00+0530". */
    private static final DateTimeFormatter IST_OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private static final DateTimeFormatter[] INPUT_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),        // React short ISO
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),     // ISO with seconds
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")        // MySQL style (with space)
    };

    /**
     * One stored wall-clock time as Remedy wants to receive it -
     * "2026-09-24T05:19:00+0530".
     *
     * The LocalDateTime carries no zone, and every time this system stores is
     * IST wall-clock (what the user typed, what the DB holds), so the offset is
     * stamped on rather than converted to: 05:19 goes out as 05:19+0530, never
     * shifted. Sending it without an offset leaves Remedy to guess the zone,
     * which is how a scheduled window lands five and a half hours off.
     */
    public static String toRemedyIst(LocalDateTime dateTime) {

        if (dateTime == null) {
            return null;
        }

        return dateTime.atZone(IST).format(IST_OUTPUT_FORMATTER);
    }


    public static String toRemedyIstFromString(String dateTime) {

        if (dateTime == null || dateTime.isBlank()) {
            return null;
        }

        return toRemedyIst(parseToLocalDateTime(dateTime));
    }

    public static LocalDateTime parseToLocalDateTime(String dateTime) {

        if (dateTime == null || dateTime.isBlank()) {
            return null;
        }

        // Offset format: 2026-09-10T10:00:00+0530
        try {
            return OffsetDateTime.parse(
                    dateTime,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
            ).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Continue with LocalDateTime formats
        }

        // No-offset formats
        for (DateTimeFormatter formatter : INPUT_FORMATTERS) {
            try {
                return LocalDateTime.parse(dateTime, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        throw new IllegalArgumentException(
                "Unsupported date-time format: " + dateTime
        );
    }



    public static Timestamp parseRemedyDateToIST(String remedyDate) {

        if (remedyDate == null || remedyDate.isBlank()) {
            return null;
        }

        try {
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

            OffsetDateTime utcDateTime =
                    OffsetDateTime.parse(remedyDate, formatter);

            ZonedDateTime istDateTime =
                    utcDateTime.atZoneSameInstant(IST);

            return Timestamp.valueOf(istDateTime.toLocalDateTime());

        } catch (Exception e) {
            System.err.println("Unable to parse date: " + remedyDate);
            return null;
        }
    }

    public static String convertIstToUtc(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) return null;

        try {
            // Case 1: Already UTC (ends with Z)
            if (dateTimeStr.endsWith("Z")) {
                Instant instant = Instant.parse(dateTimeStr);
                return instant.atZone(ZoneOffset.UTC).format(OUTPUT_FORMATTER);
            }

            // Case 2: Try parsing with multiple patterns
            LocalDateTime localDateTime = null;
            for (DateTimeFormatter formatter : INPUT_FORMATTERS) {
                try {
                    localDateTime = LocalDateTime.parse(dateTimeStr, formatter);
                    break;
                } catch (Exception ignored) {
                }
            }

            if (localDateTime == null) {
                throw new IllegalArgumentException("Unsupported date format: " + dateTimeStr);
            }

            ZonedDateTime istZoned = localDateTime.atZone(IST);
            ZonedDateTime utcZoned = istZoned.withZoneSameInstant(ZoneOffset.UTC);

            return utcZoned.format(OUTPUT_FORMATTER);

        } catch (Exception e) {
            System.err.println("Invalid date format: " + dateTimeStr);
            return dateTimeStr; // fallback
        }
    }
}