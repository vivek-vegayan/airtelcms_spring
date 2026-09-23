package com.vegayan.airtelmanagement.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String module();
    String subModule() default "";
    String action();
    String remark() default "";
    String affectedUserParam() default "";
    String actionParam() default "";
    String[] keyParams() default {};
}
