package com.vegayan.airtelmanagement.audit.aspect;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Turns an {@code @Auditable} controller method into one audit row.
 *
 * <p>This is the whole integration mechanism. A module opts in by annotating
 * an endpoint; nothing inside that endpoint - or in the service, repository or
 * procedure below it - changes at all. That is what keeps the feature additive:
 * the audit trail observes the existing workflow, it is not woven into it.
 *
 * <h3>What is and is not recorded</h3>
 * <ul>
 *   <li>Advice is {@code @AfterReturning}, so a method that threw records
 *       nothing - a failed operation must not leave a row claiming it
 *       happened.</li>
 *   <li>Several endpoints report a refusal with HTTP 200 and a
 *       {@code status: "Fail"} body. {@link #succeeded} inspects the returned
 *       value for exactly that shape and skips those too.</li>
 *   <li>Page loads are not audited because no GET that merely renders a screen
 *       carries the annotation. There is no pointcut here on controllers in
 *       general, by design.</li>
 * </ul>
 *
 * <h3>Failure policy</h3>
 * Every path through {@link #recordAction} is wrapped: an audit problem is
 * logged to the APPLICATION log and swallowed. The user has already had their
 * business result by this point and must not be shown a failure for it.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogAspect.class);

    /** Values of a {@code status} field that mean the operation was refused. */
    private static final Set<String> FAILURE_STATUSES = Set.of("fail", "failed", "error");

    private final AuditLogService auditLogService;

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void recordAction(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            if (!succeeded(result)) {
                LOGGER.debug("Not auditing {}.{} - the operation reported failure",
                        joinPoint.getSignature().getDeclaringType().getSimpleName(),
                        joinPoint.getSignature().getName());
                return;
            }

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] names = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            String action = resolveAction(auditable, names, args);
            Long affectedUserId = resolveAffectedUserId(auditable, names, args);
            String remark = buildRemark(auditable, names, args);

            auditLogService.logAction(
                    auditable.module(),
                    emptyToNull(auditable.subModule()),
                    action,
                    affectedUserId,
                    remark);

        } catch (Exception ex) {
            LOGGER.error("Audit aspect failed for {}.{} - the user action itself was unaffected",
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    joinPoint.getSignature().getName(), ex);
        }
    }

    // ------------------------------------------------------------------
    // Did the business operation actually succeed?
    // ------------------------------------------------------------------

    private boolean succeeded(Object result) {
        if (result == null) {
            // A void method that returned normally did its job.
            return true;
        }

        if (result instanceof ResponseEntity<?> response) {
            if (!response.getStatusCode().is2xxSuccessful()) {
                return false;
            }
            return bodySucceeded(response.getBody());
        }

        return bodySucceeded(result);
    }

    /**
     * Treats a payload carrying {@code status = Fail | Failed | Error} as a
     * refusal. Reading the property generically covers {@code ApiResponse}
     * (a record), {@code LoginResponseDto} and the reschedule DTOs without
     * naming any of them here.
     *
     * <p>Only those three exact words count. A CRQ whose {@code status} is
     * "DONE" or "CANCELLED" is a successful operation reporting domain state,
     * and must still be audited.
     */
    private boolean bodySucceeded(Object body) {
        if (body == null) {
            return true;
        }
        Object status = readProperty(body, "status");
        if (status instanceof String text) {
            return !FAILURE_STATUSES.contains(text.trim().toLowerCase(Locale.ROOT));
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Resolving the annotation against the actual call
    // ------------------------------------------------------------------

    /**
     * The verb, taken from {@code actionParam()} when that resolves to a known
     * status value and from {@code action()} otherwise. This is what lets a
     * single activate/deactivate endpoint record ENABLE or DISABLE rather than
     * a vague UPDATE, without splitting the endpoint in two.
     */
    private String resolveAction(Auditable auditable, String[] names, Object[] args) {
        String path = auditable.actionParam();
        if (path == null || path.isBlank()) {
            return auditable.action();
        }
        String derived = AuditAction.fromStatusValue(resolvePath(path, names, args));
        return derived != null ? derived : auditable.action();
    }

    private Long resolveAffectedUserId(Auditable auditable, String[] names, Object[] args) {
        String path = auditable.affectedUserParam();
        if (path == null || path.isBlank()) {
            return null;
        }
        Object value = resolvePath(path, names, args);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long id = number.longValue();
            return id > 0 ? id : null;
        }
        try {
            long id = Long.parseLong(String.valueOf(value).trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException ex) {
            // The path pointed at something that is not a user id. A NULL
            // affected user is a correct answer; a wrong one would not be.
            return null;
        }
    }

    /**
     * {@code remark()} with the values named by {@code keyParams()} appended,
     * e.g. {@code "Approved the CRQ [crqNo=CRQ0001]"}. Keys are what make a row
     * say which record was acted on without any endpoint composing a message
     * by hand.
     */
    private String buildRemark(Auditable auditable, String[] names, Object[] args) {
        StringBuilder remark = new StringBuilder(auditable.remark() == null ? "" : auditable.remark().trim());

        StringJoiner keys = new StringJoiner(", ", " [", "]").setEmptyValue("");
        for (String path : auditable.keyParams()) {
            Object value = resolvePath(path, names, args);
            if (value != null && !String.valueOf(value).isBlank()) {
                keys.add(shortName(path) + "=" + value);
            }
        }

        String keyText = keys.toString();
        if (!keyText.isEmpty()) {
            if (remark.isEmpty()) {
                remark.append(auditable.action());
            }
            remark.append(keyText);
        }

        return remark.isEmpty() ? null : remark.toString();
    }

    /** "request.userId" -> "userId", so a remark reads for a person. */
    private String shortName(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }

    // ------------------------------------------------------------------
    // Parameter path resolution
    // ------------------------------------------------------------------

    /**
     * Resolves {@code "userId"} or {@code "request.userId"} against the call's
     * arguments. Deliberately not SpEL: the paths needed here are a parameter
     * name plus at most a couple of property hops, and a full expression
     * engine on the request path would be both slower and a much larger
     * failure surface for something whose failure must be harmless.
     *
     * @return the value, or null if any hop cannot be resolved
     */
    private Object resolvePath(String path, String[] names, Object[] args) {
        if (path == null || path.isBlank() || names == null || args == null) {
            return null;
        }

        String[] hops = path.trim().split("\\.");

        Object current = null;
        boolean found = false;
        for (int i = 0; i < names.length && i < args.length; i++) {
            if (hops[0].equals(names[i])) {
                current = args[i];
                found = true;
                break;
            }
        }
        if (!found) {
            LOGGER.debug("Audit parameter '{}' does not match any parameter of the annotated method", hops[0]);
            return null;
        }

        for (int i = 1; i < hops.length && current != null; i++) {
            current = readProperty(current, hops[i]);
        }
        return current;
    }

    /** Getter first, then a declared field. Returns null rather than throwing. */
    private Object readProperty(Object target, String property) {
        if (target == null || property == null || property.isBlank()) {
            return null;
        }

        String capitalised = Character.toUpperCase(property.charAt(0)) + property.substring(1);

        for (String candidate : new String[]{property, "get" + capitalised, "is" + capitalised}) {
            try {
                Method method = target.getClass().getMethod(candidate);
                if (method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    return method.invoke(target);
                }
            } catch (Exception ignored) {
                // try the next shape
            }
        }

        try {
            Field field = target.getClass().getDeclaredField(property);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
