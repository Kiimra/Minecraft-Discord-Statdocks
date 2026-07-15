package org.bukkit.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compile-only stub. See org.bukkit.plugin.Plugin. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {
    EventPriority priority() default EventPriority.NORMAL;

    boolean ignoreCancelled() default false;
}
