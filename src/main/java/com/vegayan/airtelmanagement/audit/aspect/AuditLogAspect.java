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

@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogAspect.class);

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
