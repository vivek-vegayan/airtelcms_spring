package com.vegayan.airtelmanagement.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as an <em>audited user action</em>.
 *
 * <p>Adding this annotation is the entire integration cost: no existing
 * statement inside the method changes, no service signature changes, and the
 * business result the method returns is passed back to the caller untouched.
 * {@code AuditLogAspect} observes the call afterwards and writes one row
 * through {@code sp_insert_ui_actions_log}.
 *
 * <h3>Only real, successful actions are recorded</h3>
 * The aspect advises {@code @AfterReturning}, so a method that threw records
 * nothing. It additionally inspects the returned value and skips the write for
 * a non-2xx {@code ResponseEntity} or an {@code ApiResponse} whose status is
 * Fail/Error - several endpoints here report a refused operation that way with
 * HTTP 200, and an audit trail that claims those succeeded would be worse than
 * no audit trail.
 *
 * <p>Put it only on methods that change state. A GET that merely renders a
 * screen is not a user action; a GET that hands the user a document
 * ({@code DOWNLOAD}) is.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * @Auditable(module = AuditModule.USER_MANAGEMENT,
 *            subModule = AuditModule.SUB_USER,
 *            action = AuditAction.UPDATE,
 *            affectedUserParam = "request.userId",
 *            remark = "Updated user details")
 * @PutMapping("/v1/updateemp")
 * public ResponseEntity<ApiResponse> updateEmployee(...) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Business module, normally a {@code AuditModule} constant. */
    String module();

    /** Screen or entity within the module. Blank writes NULL. */
    String subModule() default "";

    /**
     * The verb, normally an {@code AuditAction} constant. Used as-is unless
     * {@link #actionParam()} resolves to something more specific.
     */
    String action();

    /**
     * Human-readable description of what the user did. Kept short - the row
     * already carries module, sub-module and verb, so this is the sentence a
     * reader needs on top of those, not a repeat of them.
     *
     * <p>Values named by {@link #keyParams()} are appended to it.
     */
    String remark() default "";

    /**
     * Parameter path whose value is the user this action was performed
     * <em>on</em>, e.g. {@code "userId"} or {@code "request.userId"} (one
     * level of bean property is supported). Left blank - the common case -
     * the affected user is NULL, which is the correct answer for an action
     * that targets a CRQ, a role or a domain rather than a person.
     *
     * <p>A path that cannot be resolved yields NULL rather than failing the
     * request.
     */
    String affectedUserParam() default "";

    /**
     * Parameter path whose value selects the verb at runtime, for the few
     * routes where one endpoint covers two opposite actions (activate vs.
     * deactivate). Resolved through {@code AuditAction.fromStatusValue};
     * anything unrecognised falls back to {@link #action()}.
     */
    String actionParam() default "";

    /**
     * Parameter paths identifying the record acted on, appended to the remark
     * as {@code [crqNo=CRQ0001]}. This is what makes a row say <em>which</em>
     * CRQ was approved without any endpoint having to build a message by hand.
     *
     * <p>Unresolvable paths are skipped silently.
     */
    String[] keyParams() default {};
}
