package com.queuewise.queue.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.queuewise.counter.persistence.CounterRepository;
import com.queuewise.queue.persistence.QueueRepository;
import com.queuewise.token.persistence.TokenRepository;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;

/**
 * Structural contract tests that verify concurrency controls are in place without
 * requiring a running database.  These mirror the acceptance criteria from
 * CONCURRENCY.md §13 that can be validated at the class/annotation level.
 */
class QueueEngineConcurrencyTest {

    @Test
    void criticalOperationsUseTransactionsAndPessimisticLocks() throws Exception {
        // All mutation methods must be @Transactional
        assertThat(method("createWalkIn").isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(method("callNext").isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(method("complete").isAnnotationPresent(Transactional.class)).isTrue();

        // Repository locking methods must use PESSIMISTIC_WRITE (SELECT … FOR UPDATE)
        assertThat(lockMode(QueueRepository.class, "findWithLockByIdAndStatus"))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(lockMode(CounterRepository.class, "findWithLockById"))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(lockMode(TokenRepository.class, "findWithLockById"))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void callNextAcceptsCallNextRequestForAuditContext() throws Exception {
        // Verifies that the service method signature includes a CallNextRequest parameter
        // so the controller can supply correlation ID and IP address to the audit record.
        Method callNext = method("callNext");
        boolean hasCallNextRequest = Arrays.stream(callNext.getParameterTypes())
                .anyMatch(type -> type.equals(CallNextRequest.class));
        assertThat(hasCallNextRequest)
                .as("callNext must accept a CallNextRequest parameter for audit context")
                .isTrue();
    }

    @Test
    void callNextRetryBudgetIsPositiveAndBounded() {
        // The retry constant must be > 0 (retries are enabled) and <= 10 (not unbounded).
        assertThat(QueueEngineService.MAX_CALL_NEXT_RETRIES)
                .as("retry budget must be positive and bounded")
                .isGreaterThan(0)
                .isLessThanOrEqualTo(10);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Method method(String name) {
        return Arrays.stream(QueueEngineService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Method not found: " + name));
    }

    private LockModeType lockMode(Class<?> repository, String name) {
        return Arrays.stream(repository.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Method not found: " + name + " on " + repository))
                .getAnnotation(Lock.class)
                .value();
    }
}
