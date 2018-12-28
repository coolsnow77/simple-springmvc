package com.hyde.simplemvc.mvcframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HYRequestMapping {
    String value() default "";
}
