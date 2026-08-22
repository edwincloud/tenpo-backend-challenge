package com.tenpo.challenge.infrastructure.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca qué métodos de controller tienen que quedar registrados en el historial
 * (requisito 3 del challenge). Lo procesa CallHistoryAspect vía AOP, así los
 * controllers ni se enteran de cómo o cuándo se guarda ese historial.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RecordHistory {

    /** Nombre del endpoint tal como debe quedar en el historial, ej: "/api/v1/calculate". */
    String endpoint();
}
