package org.aldousdev.dockflowbackend.workflow.components;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CanStartWorkflow {
    String message() default "You dont fave access to start this workflow";
}
