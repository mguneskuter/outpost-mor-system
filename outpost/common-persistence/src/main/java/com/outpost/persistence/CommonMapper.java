package com.outpost.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a MyBatis mapper interface as an Outpost-owned persistence mapper.
 *
 * <p>Only interfaces annotated with this marker are discovered and registered as MyBatis mappers by
 * {@link EnableOutpostPersistence}. The mapper interface and its XML belong to the deployable or
 * infrastructure adapter that owns the query; this marker only supplies the shared discovery
 * convention.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface CommonMapper {}
