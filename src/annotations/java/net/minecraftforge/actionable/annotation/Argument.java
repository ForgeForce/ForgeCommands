package net.minecraftforge.actionable.annotation;

public @interface Argument {
    StringType stringType() default StringType.STRING;

    String defaultValue() default "";
    boolean optional() default false;

    String description() default "";
}
