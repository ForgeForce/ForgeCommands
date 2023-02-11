package net.minecraftforge.actionable.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Value {
    String[] names() default {};
    String description() default "";
}
