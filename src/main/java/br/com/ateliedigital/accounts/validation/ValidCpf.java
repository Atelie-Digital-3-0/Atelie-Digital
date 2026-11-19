package br.com.ateliedigital.accounts.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.br.CPF;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * CPF obrigatorio, com 11 digitos (sem pontuacao) e digitos verificadores corretos.
 * <p>
 * Compoe constraints prontas, sem classe validadora propria: {@link CPF} (Hibernate Validator) confere os
 * digitos verificadores, mas aceita sequencias repetidas como {@code 11111111111}, que o regex abaixo rejeita.
 */
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
@ReportAsSingleViolation
@NotBlank
@Pattern(regexp = "(?!(\\d)\\1{10})\\d{11}")
@CPF
public @interface ValidCpf {
    String message() default "cpf must be a valid CPF with 11 digits";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
