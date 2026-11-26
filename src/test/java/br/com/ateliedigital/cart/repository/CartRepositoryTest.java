package br.com.ateliedigital.cart.repository;

import br.com.ateliedigital.cart.config.CartProperties;
import br.com.ateliedigital.cart.model.CartItem;
import br.com.ateliedigital.exceptions.RuleConflit;
import br.com.ateliedigital.exceptions.VioletedRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes unitarios do {@link CartRepository} com o {@link RedisTemplate} mockado. Validam a montagem da chave, a
 * ordenacao e, principalmente, o protocolo otimista {@code WATCH/MULTI/EXEC} com retentativas. O comportamento
 * contra um Redis real fica no {@code CartRepositoryIntegrationTest}.
 * <p>
 * Tecnica: <b>teste baseado em estados/transicoes</b> do laco de retentativa (grava na 1a tentativa, grava apos
 * conflitos, esgota as 10 tentativas, alteracao recusada, thread interrompida).
 */
@ExtendWith(MockitoExtension.class)
class CartRepositoryTest {
    private static final UUID USER = UUID.fromString("5f1c3a52-8d2e-4a7b-9c41-0e6f2b7d9a10");
    private static final String KEY = "test-cart:" + USER;
    private static final int MAX_ATTEMPTS = 10;

    @Mock
    private RedisTemplate<String, CartItem> redis;
    @Mock
    private HashOperations<String, String, CartItem> hash;
    @Mock
    private RedisOperations<String, CartItem> session;
    @Mock
    private HashOperations<String, String, CartItem> sessionHash;

    private CartRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CartRepository(redis, new CartProperties(99, 50, "test-cart:"));
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Nested
    @DisplayName("Leitura")
    class Reads {

        @Test
        @DisplayName("findAll usa a chave <prefixo><usuario>, ignora valores nulos e ordena por added_at (nulos no fim)")
        void findAllSortsByAddedAt() {
            CartItem newest = item("1", Instant.parse("2026-11-20T00:00:00Z"));
            CartItem oldest = item("2", Instant.parse("2026-11-01T00:00:00Z"));
            CartItem withoutDate = item("3", null);
            when(redis.<String, CartItem>opsForHash()).thenReturn(hash);
            when(hash.values(KEY)).thenReturn(new ArrayList<>(Arrays.asList(withoutDate, newest, null, oldest)));

            assertThat(repository.findAll(USER)).containsExactly(oldest, newest, withoutDate);
        }

        @Test
        @DisplayName("find devolve o item quando existe e vazio quando nao existe")
        void find() {
            CartItem item = item("7", Instant.now());
            when(redis.<String, CartItem>opsForHash()).thenReturn(hash);
            when(hash.get(KEY, "7")).thenReturn(item);

            assertThat(repository.find(USER, "7")).contains(item);
            assertThat(repository.find(USER, "8")).isEmpty();
        }

        @Test
        @DisplayName("exists consulta o campo da variante no hash do usuario")
        void exists() {
            when(redis.<String, CartItem>opsForHash()).thenReturn(hash);
            when(hash.hasKey(KEY, "7")).thenReturn(true);

            assertThat(repository.exists(USER, "7")).isTrue();
            assertThat(repository.exists(USER, "8")).isFalse();
        }
    }

    @Nested
    @DisplayName("Remocao")
    class Removals {

        @Test
        @DisplayName("remove devolve true quando o campo existia (1 removido) e false quando nao (0 removidos)")
        void removeReportsWhetherItExisted() {
            when(redis.<String, CartItem>opsForHash()).thenReturn(hash);
            when(hash.delete(KEY, "7")).thenReturn(1L);
            when(hash.delete(KEY, "8")).thenReturn(0L);

            assertThat(repository.remove(USER, "7")).isTrue();
            assertThat(repository.remove(USER, "8")).isFalse();
        }

        @Test
        @DisplayName("removeAll apaga todos os campos em uma unica chamada")
        void removeAllDeletesInOneCall() {
            when(redis.<String, CartItem>opsForHash()).thenReturn(hash);

            repository.removeAll(USER, List.of("1", "2"));

            verify(hash).delete(KEY, "1", "2");
        }

        @Test
        @DisplayName("removeAll com lista vazia nao chama o Redis")
        void removeAllEmptyDoesNothing() {
            repository.removeAll(USER, List.of());

            verifyNoInteractions(redis);
        }

        @Test
        @DisplayName("clear apaga a chave inteira do usuario")
        void clearDeletesKey() {
            repository.clear(USER);

            verify(redis).delete(KEY);
        }
    }

    @Nested
    @DisplayName("save: gravacao otimista (WATCH/MULTI/EXEC)")
    class Save {

        @BeforeEach
        void runSessionCallbacksOnMockedSession() {
            when(redis.execute(any(SessionCallback.class)))
                    .thenAnswer(invocation -> invocation.<SessionCallback<?>>getArgument(0).execute(session));
            when(session.<String, CartItem>opsForHash()).thenReturn(sessionHash);
        }

        @Test
        @DisplayName("Sem concorrencia: WATCH, le o estado, MULTI, grava e EXEC, nessa ordem, em uma tentativa")
        void savesOnFirstAttempt() {
            CartItem current = item("7", Instant.now());
            CartItem updated = item("7", Instant.now());
            when(sessionHash.get(KEY, "7")).thenReturn(current);
            when(sessionHash.size(KEY)).thenReturn(4L);
            when(session.exec()).thenReturn(List.of(true));
            List<Object[]> calls = new ArrayList<>();

            CartItem saved = repository.save(USER, "7", (item, count) -> {
                calls.add(new Object[]{item, count});
                return updated;
            });

            assertThat(saved).isSameAs(updated);
            assertThat(calls).singleElement().satisfies(call -> assertThat(call).containsExactly(current, 4L));
            InOrder order = inOrder(session, sessionHash);
            order.verify(session).watch(KEY);
            order.verify(sessionHash).get(KEY, "7");
            order.verify(session).multi();
            order.verify(sessionHash).put(KEY, "7", updated);
            order.verify(session).exec();
            verify(redis, times(1)).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("EXEC descartado (lista vazia ou nula) por alteracao concorrente: refaz sobre o estado novo")
        void retriesWhenTransactionIsDiscarded() {
            CartItem stale = item("7", Instant.now());
            CartItem fresh = item("7", Instant.now());
            when(sessionHash.get(KEY, "7")).thenReturn(stale, stale, fresh);
            when(session.exec()).thenReturn(List.of(), null, List.of(true));
            List<CartItem> seen = new ArrayList<>();

            CartItem saved = repository.save(USER, "7", (item, count) -> {
                seen.add(item);
                return item;
            });

            assertThat(saved).isSameAs(fresh);
            assertThat(seen).containsExactly(stale, stale, fresh);
            verify(redis, times(3)).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("Conflito nas 10 tentativas: 409 (RuleConflit) e a alteracao foi tentada exatamente 10 vezes")
        void givesUpAfterMaxAttempts() {
            when(session.exec()).thenReturn(List.of());
            AtomicInteger attempts = new AtomicInteger();

            assertThatThrownBy(() -> repository.save(USER, "7", (item, count) -> {
                attempts.incrementAndGet();
                return item("7", Instant.now());
            }))
                    .isInstanceOf(RuleConflit.class)
                    .hasMessage("Cart was modified concurrently, please try again");
            assertThat(attempts).hasValue(MAX_ATTEMPTS);
        }

        @Test
        @DisplayName("Alteracao recusada pela regra de negocio: UNWATCH, nada e gravado e nao ha nova tentativa")
        void rejectedChangeIsNotRetried() {
            VioletedRule rejection = new VioletedRule("Insufficient stock: only 1 units available");

            assertThatThrownBy(() -> repository.save(USER, "7", (item, count) -> {
                throw rejection;
            })).isSameAs(rejection);

            verify(session).unwatch();
            verify(session, never()).multi();
            verify(sessionHash, never()).put(anyString(), anyString(), any());
            verify(session, never()).exec();
            verify(redis, times(1)).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("Thread interrompida durante a espera entre tentativas: 409 e o sinal de interrupcao e mantido")
        void interruptedBackoff() {
            when(session.exec()).thenReturn(List.of());
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> repository.save(USER, "7", (item, count) -> item("7", Instant.now())))
                    .isInstanceOf(RuleConflit.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(redis, times(1)).execute(any(SessionCallback.class));
        }
    }

    private static CartItem item(String id, Instant addedAt) {
        return new CartItem(id, "product-" + id, "store-1", 1, new BigDecimal("10.00"), "SKU-" + id, "azul", "M",
                addedAt, addedAt);
    }
}
