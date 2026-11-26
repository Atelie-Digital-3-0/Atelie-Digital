package br.com.ateliedigital.cart.dto;

import br.com.ateliedigital.cart.dto.CartRequests.AddCartItemRequest;
import br.com.ateliedigital.cart.dto.CartRequests.UpdateCartItemRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validacao dos corpos das requisicoes do carrinho (Bean Validation), sem subir o Spring.
 * <p>
 * Tecnicas: <b>classes de equivalencia</b> para o id da variante (numerico, UUID, alfanumerico com hifen x vazio,
 * espacos, separadores de chave do Redis, curingas) e <b>valor-limite</b> no tamanho do id (1, 64, 65) e nas
 * quantidades (-1, 0, 1, {@link Integer#MAX_VALUE}).
 */
class CartRequestsValidationTest {
    private final Validator validator = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory().getValidator();

    @Nested
    @DisplayName("product_variant_id")
    class VariantId {

        @ParameterizedTest(name = "[{index}] \"{0}\" e aceito")
        @ValueSource(strings = {
                "7",                                       // tamanho minimo (1)
                "101",                                     // id numerico do catalogo
                "4f9d2c1e-7b3a-4e8f-9a6d-2c5b1e0f3a7d",    // UUID
                "CAN-AZUL-M",                              // alfanumerico com hifen
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" // tamanho maximo (64)
        })
        @DisplayName("Ids validos")
        void acceptsValidIds(String id) {
            assertThat(validator.validate(new AddCartItemRequest(id, 1))).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" e recusado")
        @ValueSource(strings = {
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 65 caracteres
                "7:1",          // separador de chave do Redis
                "cart:*",       // tentativa de curinga
                "7 8",          // espaco interno
                " 7",           // espaco a esquerda
                "7_8",          // underscore fora do padrao
                "7/8",          // barra (quebraria a URL)
                "ç7"            // caractere nao ASCII
        })
        @DisplayName("Ids invalidos")
        void rejectsInvalidIds(String id) {
            assertThat(messages(validator.validate(new AddCartItemRequest(id, 1))))
                    .containsExactly("product_variant_id is invalid");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" e obrigatorio")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("Id ausente ou em branco")
        void rejectsBlankIds(String id) {
            assertThat(messages(validator.validate(new AddCartItemRequest(id, 1))))
                    .contains("product_variant_id is required");
        }
    }

    @Nested
    @DisplayName("quantity ao adicionar (POST)")
    class AddQuantity {

        @ParameterizedTest(name = "[{index}] quantity={0} e aceita")
        @ValueSource(ints = {1, 99, Integer.MAX_VALUE})
        @DisplayName("Quantidades positivas passam na validacao (os limites de negocio ficam no servico)")
        void acceptsPositive(int quantity) {
            assertThat(validator.validate(new AddCartItemRequest("7", quantity))).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] quantity={0} e recusada")
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        @DisplayName("Zero e negativas sao recusadas")
        void rejectsZeroOrNegative(int quantity) {
            assertThat(messages(validator.validate(new AddCartItemRequest("7", quantity))))
                    .containsExactly("quantity must be greater than zero");
        }

        @Test
        @DisplayName("Quantidade ausente e recusada")
        void rejectsNull() {
            assertThat(messages(validator.validate(new AddCartItemRequest("7", null))))
                    .containsExactly("quantity is required");
        }
    }

    @Nested
    @DisplayName("quantity ao atualizar (PATCH)")
    class UpdateQuantity {

        @ParameterizedTest(name = "[{index}] quantity={0} e aceita")
        @ValueSource(ints = {0, 1, 99})
        @DisplayName("Zero (remover) e positivas sao aceitas")
        void acceptsZeroOrPositive(int quantity) {
            assertThat(validator.validate(new UpdateCartItemRequest(quantity))).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] quantity={0} e recusada")
        @ValueSource(ints = {-1, Integer.MIN_VALUE})
        @DisplayName("Negativas sao recusadas")
        void rejectsNegative(int quantity) {
            assertThat(messages(validator.validate(new UpdateCartItemRequest(quantity))))
                    .containsExactly("quantity must be zero or greater");
        }

        @Test
        @DisplayName("Quantidade ausente e recusada")
        void rejectsNull() {
            assertThat(messages(validator.validate(new UpdateCartItemRequest(null))))
                    .containsExactly("quantity is required");
        }
    }

    @Test
    @DisplayName("O mesmo padrao do body e usado na URL (constante publica)")
    void patternIsShared() {
        assertThat(CartRequests.VARIANT_ID_PATTERN).isEqualTo("[A-Za-z0-9-]{1,64}");
    }

    private static <T> java.util.List<String> messages(Set<ConstraintViolation<T>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).toList();
    }
}
