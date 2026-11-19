package br.com.ateliedigital.accounts.dto;

import br.com.ateliedigital.accounts.dto.AccountRequests.CreateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.UpdateAccountRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountRequestsValidationTest {
    private final Validator validator = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "11144477735", "12345678909", "10000000108"})
    void acceptsValidCpf(String cpf) {
        assertTrue(validator.validate(create(cpf)).isEmpty(), cpf);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "52998224726",       // digito verificador errado
            "12345678901",       // exemplo classico invalido
            "11111111111",       // sequencias repetidas passam no calculo, mas nao sao CPFs validos
            "00000000000",
            "99999999999",
            "5299822472",        // 10 digitos
            "529982247250",      // 12 digitos
            "529.982.247-25",    // com pontuacao: a API recebe so digitos
            "5299822472a"})
    void rejectsInvalidCpf(String cpf) {
        assertFalse(validator.validate(create(cpf)).isEmpty(), "[" + cpf + "]");
    }

    @Test
    void updateAcceptsEmptyPayloadBecauseEveryFieldIsOptional() {
        assertTrue(validator.validate(new UpdateAccountRequest(null, null, null, null, null, null)).isEmpty());
    }

    @Test
    void updateAcceptsASingleField() {
        assertTrue(validator.validate(new UpdateAccountRequest(null, null, null, null, null, "nova bio")).isEmpty());
        assertTrue(validator.validate(new UpdateAccountRequest("Maria", null, null, null, null, null)).isEmpty());
    }

    @Test
    void updateStillValidatesFieldsThatWereSent() {
        assertEquals(1, validator.validate(new UpdateAccountRequest("  ", null, null, null, null, null)).size());
        assertEquals(1, validator.validate(new UpdateAccountRequest(null, "", null, null, null, null)).size());
        assertEquals(1, validator.validate(new UpdateAccountRequest(null, null, " ", null, null, null)).size());
        assertEquals(1, validator.validate(new UpdateAccountRequest(null, null, null, null, "nao-e-email", null)).size());
        assertEquals(1, validator.validate(
                new UpdateAccountRequest(null, null, null, LocalDate.now().plusDays(1), null, null)).size());
    }

    private static CreateAccountRequest create(String cpf) {
        return new CreateAccountRequest("Maria", "Silva", "11999999999", LocalDate.of(1990, 1, 1), cpf,
                "maria@teste.dev");
    }
}
