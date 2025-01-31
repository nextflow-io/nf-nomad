package nextflow.nomad.executor

import dev.failsafe.Failsafe
import dev.failsafe.RetryPolicy
import dev.failsafe.event.EventListener
import dev.failsafe.event.ExecutionAttemptedEvent
import dev.failsafe.function.CheckedSupplier
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.nomadproject.client.ApiException
import nextflow.nomad.config.RetryConfig

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException
import java.util.function.Predicate

@Slf4j
@CompileStatic
class FailsafeExecutor {

    private RetryConfig config

    FailsafeExecutor(RetryConfig config){
        this.config = config
    }

    protected <T> RetryPolicy<T> retryPolicy(Predicate<? extends Throwable> cond) {

        final listener = new EventListener<ExecutionAttemptedEvent<T>>() {
            @Override
            void accept(ExecutionAttemptedEvent<T> event) throws Throwable {
                log.debug("Nomad TooManyRequests response error - attempt: ${event.attemptCount}; reason: ${event.lastFailure.message}")
            }
        }
        return RetryPolicy.<T>builder()
                .handleIf(cond)
                .withBackoff(config.delay.toMillis(), config.maxDelay.toMillis(), ChronoUnit.MILLIS)
                .withMaxAttempts(config.maxAttempts)
                .withJitter(config.jitter)
                .onRetry(listener)
                .build()
    }

    /*
    408 Request Timeout
    429 Too Many Requests
    500 Internal Server Error
    502 Bad Gateway
    503 Service Unavailable
    504 Gateway Timeout
     */
    final private static List<Integer> RETRY_CODES = List.of(408, 429, 500, 502, 503, 504)

    protected <T> T apply(CheckedSupplier<T> action) {
        // define the retry condition
        final cond = new Predicate<? extends Throwable>() {
            @Override
            boolean test(Throwable t) {
                if( t instanceof ApiException && t.code in RETRY_CODES )
                    return true
                if( t instanceof IOException || t.cause instanceof IOException )
                    return true
                if( t instanceof TimeoutException || t.cause instanceof TimeoutException )
                    return true
                return false
            }
        }
        // create the retry policy object
        final policy = retryPolicy(cond)
        // apply the action with
        return Failsafe.with(policy).get(action)
    }

}
