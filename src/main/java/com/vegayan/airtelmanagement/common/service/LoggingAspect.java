package com.vegayan.airtelmanagement.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.config.LogConfig;
import com.vegayan.airtelmanagement.common.dto.LogType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {


    private final ObjectMapper objectMapper;

    private static final String SEPARATOR = "----------------------------------------------------------------------------------------------------";
    private static final String LOG_START = "[START] CLIENT_REQUEST | id={} | class={} | method={} | payload={}";
    private static final String LOG_DB_REQUEST = "[STEP] APP_TO_DB | id={} | class={} | method={} | request={}";
    private static final String LOG_DB_RESPONSE = "[STEP] DB_RESPONSE | id={} | class={} | method={} | response={}";
    private static final String LOG_DB_ERROR = "[STEP] DB_RESPONSE | id={} | class={} | method={} | error={}";
    private static final String LOG_SUCCESS_RESPONSE = "[END] APP_TO_CLIENT_RESPONSE | id={} | timeMs={}ms | response={}";
    private static final String LOG_ERROR_RESPONSE = "[ERROR] APP_TO_CLIENT_RESPONSE | id={} | timeMs={}ms | response={}";

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerLayer() {}

    @Pointcut("within(@org.springframework.stereotype.Repository *)")
    public void repositoryLayer() {}

    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceLayer() {}

    @Around(
            "controllerLayer() || " +
                    "serviceLayer() || " +
                    "repositoryLayer() || " +
                    "@annotation(com.vegayan.airtelmanagement.common.dto.LogType)"
    )
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        String correlationId = Optional.ofNullable(MDC.get("correlationId"))
                .orElseGet(() -> {
                    String id = UUID.randomUUID().toString();
                    MDC.put("correlationId", id);
                    return id;
                });

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String layer = resolveLayer(joinPoint);

        // Resolve Logger and Config
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        LogConfig config = method.getAnnotation(LogConfig.class);

        String loggerName = Optional.ofNullable(resolveLogger(joinPoint)).orElse("ROOT");
        Logger logger = LoggerFactory.getLogger(loggerName);

        Object result = null;
        boolean isError = false;
        String responseData = "";

        try {
            // ===== START LOGGING =====
            if ("CONTROLLER".equals(layer)) {
                logger.info(SEPARATOR);
                String payload;
                if (config != null && config.excludePayload()) {
                    payload = "[EXCLUDED]";
                } else {
                    payload = getRawRequestBody();
                    if (payload == null || payload.isBlank()) {
                        payload = safeArgs(joinPoint);
                    }
                }
                logger.info(LOG_START, correlationId, className, methodName, payload);
            }

            if ("SERVICE".equals(layer)) {
                logger.info("[SERVICE_START] id={} | class={} | method={} | request={}",
                        correlationId, className, methodName, safeArgs(joinPoint));
            }

            if ("REPOSITORY".equals(layer)) {
                logger.info(LOG_DB_REQUEST, correlationId, className, methodName, safeArgs(joinPoint));
            }

            // PROCEED
            result = joinPoint.proceed();

            if ("SERVICE".equals(layer)) {
                logger.info("[SERVICE_END] id={} | class={} | method={} | response={}",
                        correlationId, className, methodName, safeObject(result));
            }

            if ("REPOSITORY".equals(layer)) {
                logger.info(LOG_DB_RESPONSE, correlationId, className, methodName, safeObject(result));
            }

            // Capture successful response for controller
            if ("CONTROLLER".equals(layer)) {
                if (config != null && config.excludeResponse()) {
                    responseData = "[RESPONSE_EXCLUDED_OR_BINARY]";
                } else {
                    Object responseBody = result instanceof ResponseEntity<?>
                            ? ((ResponseEntity<?>) result).getBody()
                            : result;
                    responseData = safeObject(responseBody);
                }
            }

        } catch (Exception ex) {
            isError = true;
            long timeTaken = System.currentTimeMillis() - startTime;

            if ("REPOSITORY".equals(layer)) {
                logger.error(LOG_DB_ERROR, correlationId, className, methodName, ex.getMessage());
            }
            if ("SERVICE".equals(layer)) {
                logger.error("[SERVICE_ERROR] id={} | class={} | method={} | error={}",
                        correlationId, className, methodName, ex.getMessage());
            }
            if ("CONTROLLER".equals(layer)) {
                responseData = safeObject(Map.of("status", "Fail", "message", ex.getMessage()));
                logger.error(LOG_ERROR_RESPONSE, correlationId, timeTaken, responseData);
            }

            throw ex;

        } finally {
            // FIX: Ensure MDC is always cleared and Controller block completes cleanly
            if ("CONTROLLER".equals(layer)) {
                long timeTaken = System.currentTimeMillis() - startTime;

                // Only log the success response if it wasn't an error (error already logged in catch block)
                if (!isError) {
                    if (responseData.contains("\"status\":\"Fail\"")) {
                        logger.error(LOG_ERROR_RESPONSE, correlationId, timeTaken, responseData);
                    } else {
                        logger.info(LOG_SUCCESS_RESPONSE, correlationId, timeTaken, responseData);
                    }
                }

                logger.info(SEPARATOR);
                MDC.clear();
            }
        }

        return result;
    }

    private String resolveLayer(ProceedingJoinPoint joinPoint) {
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());

        if (AnnotatedElementUtils.hasAnnotation(targetClass, org.springframework.web.bind.annotation.RestController.class) ||
                AnnotatedElementUtils.hasAnnotation(targetClass, org.springframework.stereotype.Controller.class)) {
            return "CONTROLLER";
        }
        if (AnnotatedElementUtils.hasAnnotation(targetClass, org.springframework.stereotype.Service.class)) {
            return "SERVICE";
        }
        if (AnnotatedElementUtils.hasAnnotation(targetClass, org.springframework.stereotype.Repository.class)) {
            return "REPOSITORY";
        }
        return "UNKNOWN";
    }

    private String resolveLogger(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        LogType methodAnn = method.getAnnotation(LogType.class);
        if (methodAnn != null) return methodAnn.value();

        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        LogType classAnn = targetClass.getAnnotation(LogType.class);
        if (classAnn != null) return classAnn.value();

        return null;
    }

    private String safeArgs(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            Parameter[] parameters = signature.getMethod().getParameters();
            Map<String, Object> paramMap = new LinkedHashMap<>();

            for (int i = 0; i < args.length; i++) {
                if (parameters[i].isAnnotationPresent(AuthenticationPrincipal.class) ||
                        args[i] instanceof org.springframework.ui.Model) {
                    continue;
                }
                String name = (parameterNames != null) ? parameterNames[i] : "arg" + i;
                paramMap.put(name, args[i]);
            }
            return objectMapper.writeValueAsString(paramMap);
        } catch (Exception e) {
            return "[UNSERIALIZABLE]";
        }
    }

//    private String safeObject(Object obj) {
//        try {
//            return objectMapper.writeValueAsString(obj);
//        } catch (Exception e) {
//            return "UNSERIALIZABLE_RESPONSE";
//        }
//    }

    private String safeObject(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            // Log the actual serialization error to your console/logs
            log.warn("Failed to serialize {}: {}",
                    obj != null ? obj.getClass().getSimpleName() : "null",
                    e.getMessage());
            return "UNSERIALIZABLE_RESPONSE";
        }
    }

    private String getRawRequestBody() {

        try {

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                return null;
            }

            HttpServletRequest request = attributes.getRequest();

            if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
                return null;
            }

            byte[] buf = wrapper.getContentAsByteArray();

            if (buf.length == 0) {
                return null;
            }

            return new String(buf, StandardCharsets.UTF_8);

        } catch (Exception e) {
            return null;
        }
    }


}
